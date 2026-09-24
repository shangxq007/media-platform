-- Artifact-only completion. V1-V18 are immutable history; this migration is
-- deliberately transactional and fails before any rename when historical data
-- is ambiguous or incomplete.
do $$
begin
    if exists (select 1 from media_asset_retired where tenant_id is null or id is null or project_id is null or storage_key is null or storage_key = '') then
        raise exception 'V19_MEDIA_ASSET_INCOMPLETE_SCOPE_OR_STORAGE';
    end if;
    if exists (select 1 from media_asset_retired m join artifact_media_details d on d.legacy_media_asset_id = m.id
               group by m.id having count(*) > 1) then
        raise exception 'V19_MEDIA_ASSET_AMBIGUOUS_MAPPING';
    end if;
    if exists (select 1 from artifact_legacy_media_link group by media_asset_id having count(distinct artifact_id) > 1) then
        raise exception 'V19_MEDIA_ASSET_AMBIGUOUS_ARTIFACT_LINK';
    end if;
end $$;

alter table artifact add column if not exists lifecycle_state varchar(32);
alter table artifact add column if not exists lifecycle_changed_at timestamptz;
alter table artifact add column if not exists audit_provenance jsonb not null default '{}'::jsonb;
alter table artifact add constraint chk_artifact_lifecycle_state check (lifecycle_state is null or lifecycle_state in ('DRAFT','AVAILABLE','TOMBSTONED','ARCHIVED','FAILED'));

-- Fill linked Artifact rows from the authoritative historical record. Any
-- conflicting digest or scope is rejected instead of silently choosing a row.
do $$
begin
    if exists (
        select 1 from media_asset_retired m
        join artifact_legacy_media_link l on l.media_asset_id=m.id
        join artifact a on a.id=l.artifact_id
        where (a.tenant_id <> m.tenant_id)
           or (coalesce(a.project_id,'') <> coalesce(m.project_id,''))
           or (a.content_digest is not null and m.checksum is not null and m.checksum <> '' and a.content_digest <> m.checksum)
    ) then raise exception 'V19_ARTIFACT_LINK_SCOPE_OR_DIGEST_CONFLICT'; end if;
end $$;

update artifact a set workspace_id = coalesce(nullif(a.workspace_id,''), m.project_id),
    storage_reference = coalesce(nullif(a.storage_reference,''), m.storage_key),
    lifecycle_state = coalesce(a.lifecycle_state, case m.publish_status when 'ARCHIVED' then 'ARCHIVED' when 'DRAFT' then 'DRAFT' else 'AVAILABLE' end),
    lifecycle_changed_at = coalesce(a.lifecycle_changed_at, m.updated_at, m.created_at),
    audit_provenance = a.audit_provenance || jsonb_build_object('legacyMediaAssetId',m.id,'ownerId',m.owner_id,'license',m.license,'retentionPolicy',m.retention_policy,'securityLevel',m.security_level,'containsPii',m.contains_pii,'aiGenerated',m.ai_generated,'mediaVersion',m.media_version),
    provenance = a.provenance || jsonb_build_object('legacyMediaAssetId',m.id,'relationship',l.relationship),
    source_lineage = a.source_lineage || jsonb_build_array(jsonb_build_object('legacyMediaAssetId',m.id,'relationship',l.relationship))
from media_asset_retired m join artifact_legacy_media_link l on l.media_asset_id=m.id where a.id=l.artifact_id;

-- Preserve canonical probe/stream facts under the same Artifact key. Multiple
-- streams are retained in the typed JSON payload; no MediaAsset root remains.
update artifact_media_details d set
    codec = coalesce(d.codec, s.codec), width = coalesce(d.width, s.width), height = coalesce(d.height, s.height),
    sample_rate = coalesce(d.sample_rate, s.sample_rate), channel_layout = coalesce(d.channel_layout, s.channel_layout),
    tracks = coalesce((select jsonb_agg(jsonb_build_object('kind', ms.stream_kind, 'codec', ms.codec, 'index', ms.stream_index, 'channels', ms.channels) order by ms.stream_index) from media_stream ms where ms.media_asset_id = m.id), d.tracks)
from artifact_legacy_media_link l join media_asset_retired m on m.id=l.media_asset_id
join lateral (select ms.codec,ms.width,ms.height,ms.sample_rate,ms.channel_layout from media_stream ms where ms.media_asset_id=m.id order by ms.stream_index limit 1) s on true
where d.artifact_id=l.artifact_id;

insert into artifact (id, tenant_id, project_id, workspace_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, storage_reference, lifecycle_state, lifecycle_changed_at, audit_provenance, created_at)
select m.id,m.tenant_id,m.project_id,m.project_id,coalesce(nullif(m.checksum,''),'legacy-media:'||m.id),coalesce(m.size_bytes,0),m.media_type,'SOURCE_MEDIA',case when m.publish_status='ARCHIVED' then 'TOMBSTONED' else 'AVAILABLE' end,1,m.storage_key,case when m.publish_status='ARCHIVED' then 'ARCHIVED' when m.publish_status='DRAFT' then 'DRAFT' else 'AVAILABLE' end,coalesce(m.updated_at,m.created_at),jsonb_build_object('legacyMediaAssetId',m.id,'ownerId',m.owner_id,'license',m.license,'retentionPolicy',m.retention_policy,'securityLevel',m.security_level,'containsPii',m.contains_pii,'aiGenerated',m.ai_generated,'mediaVersion',m.media_version),m.created_at
from media_asset_retired m where not exists (select 1 from artifact a where a.id=m.id);

alter table artifact alter column workspace_id set not null;
alter table artifact_media_details add constraint fk_media_details_artifact_scope foreign key (artifact_id) references artifact(id) on delete restrict;
create or replace function reject_artifact_identity_mutation() returns trigger language plpgsql as $$ begin
    if new.id is distinct from old.id or new.tenant_id is distinct from old.tenant_id or new.workspace_id is distinct from old.workspace_id or new.content_digest is distinct from old.content_digest or new.storage_reference is distinct from old.storage_reference or new.source_lineage is distinct from old.source_lineage then raise exception 'V19_ARTIFACT_IDENTITY_IMMUTABLE'; end if; return new; end $$;
drop trigger if exists artifact_identity_immutable on artifact;
create trigger artifact_identity_immutable before update on artifact for each row execute function reject_artifact_identity_mutation();

alter table platform_execution_result drop column if exists media_asset_id;
alter table platform_execution_result drop column if exists source_revision;
alter table platform_execution_result drop constraint if exists platform_execution_result_materialization_state_check;
alter table platform_execution_result add constraint platform_execution_result_materialization_state_check check (materialization_state in ('STORAGE_ISSUED','ARTIFACT_COMMITTED','FAILED','COMPENSATING'));
