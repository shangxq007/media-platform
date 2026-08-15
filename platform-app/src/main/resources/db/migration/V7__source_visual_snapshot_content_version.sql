-- ROADMAP_18 CIP2F/CIP2G (FINAL CONTENT VERSION INTEGRITY):
-- F2 cardinality: one logical MediaStream may be interpreted from multiple
-- Artifact content versions -> snapshot identity = (media_stream_id, artifact_id).
-- Snapshots are append-only immutable canonical facts: semantic UPDATE of
-- identity/payload fields is rejected by PostgreSQL itself.

-- 1. Composite exact-content snapshot PK (F2 multi-version coexistence)
alter table source_visual_description_snapshot
    drop constraint source_visual_description_snapshot_pkey;

alter table source_visual_description_snapshot
    add constraint pk_svd_stream_artifact primary key (media_stream_id, artifact_id);

-- 2. DB-level snapshot immutability: no semantic rebind / payload rewrite
create or replace function trg_fn_svd_snapshot_immutable() returns trigger as $$
begin
    if new.media_stream_id is distinct from old.media_stream_id
       or new.media_asset_id is distinct from old.media_asset_id
       or new.artifact_id is distinct from old.artifact_id
       or new.canonical_payload is distinct from old.canonical_payload then
        raise exception 'SOURCE_VISUAL_SNAPSHOT_IMMUTABLE: semantic mutation of '
            'source_visual_description_snapshot is forbidden (append-only canonical fact)';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_svd_snapshot_immutable
    before update on source_visual_description_snapshot
    for each row execute function trg_fn_svd_snapshot_immutable();

-- created_at remains mutable-neutral (lifecycle bookkeeping); semantic fields
-- above are frozen. FK cascade deletes (media lifecycle) are NOT blocked.
