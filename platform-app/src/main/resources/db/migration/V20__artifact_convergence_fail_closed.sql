-- Forward-only V20 hardens the already-applied V19 boundary. It never rewrites
-- V1-V19 and aborts the transaction before accepting unverifiable data.
do $$
begin
    if exists (select 1 from artifact_legacy_media_link group by media_asset_id having count(*) > 1) then
        raise exception 'V20_DUPLICATE_ARTIFACT_LINK';
    end if;
    if exists (select 1 from media_asset_retired
               where id is null or tenant_id is null or project_id is null or project_id = ''
                  or storage_key is null or storage_key = '' or checksum is null or checksum = ''
                  or size_bytes is null or size_bytes < 0 or media_type is null or media_type = ''
                  or publish_status is null or publish_status not in ('DRAFT','PUBLISHED','ARCHIVED')
                  or created_at is null) then
        raise exception 'V20_INVALID_MEDIA_FACTS';
    end if;
    if exists (select 1 from artifact
               where tenant_id is null or tenant_id = '' or workspace_id is null or workspace_id = ''
                  or content_digest is null or content_digest = '' or byte_length is null or byte_length < 0
                  or storage_reference is null or storage_reference = '' or lifecycle_state is null
                  or audit_provenance is null or provenance is null or source_lineage is null) then
        raise exception 'V20_INVALID_ARTIFACT_FACTS';
    end if;
    if exists (select 1 from media_asset_retired m join artifact_legacy_media_link l on l.media_asset_id=m.id
               join artifact a on a.id=l.artifact_id
               where a.tenant_id <> m.tenant_id or a.workspace_id <> m.project_id
                  or a.content_digest <> m.checksum or a.byte_length <> m.size_bytes
                  or a.storage_reference <> m.storage_key) then
        raise exception 'V20_ARTIFACT_SCOPE_DIGEST_STORAGE_CONFLICT';
    end if;
end $$;

-- No defaults, repair, or destructive rollback is permitted after this point.
-- Flyway rollback is a forward compensating migration only; physical object
-- writes and external effects are outside this transaction boundary.
