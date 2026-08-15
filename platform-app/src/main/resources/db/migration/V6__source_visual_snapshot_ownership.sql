-- ROADMAP_18 CIP2D (SOURCE_VISUAL_SNAPSHOT_RELATIONAL_OWNERSHIP_IS_DATABASE_ENFORCED_V1):
-- PostgreSQL must itself reject cross-asset / cross-stream / unlinked bindings.
-- No application/service-only ownership validation.

-- 1. media_stream: UNIQUE(id, media_asset_id) enables the composite ownership FK
alter table media_stream
    add constraint uq_ms_id_asset unique (id, media_asset_id);

-- 2. media_asset_artifact: UNIQUE(media_asset_id, artifact_id) enables the
--    composite artifact-ownership FK (existing PK also includes relationship)
alter table media_asset_artifact
    add constraint uq_maa_asset_artifact unique (media_asset_id, artifact_id);

-- 3. snapshot -> stream ownership: snapshot.media_stream_id MUST be owned by
--    snapshot.media_asset_id (rejects D1: stream of asset A + asset B)
alter table source_visual_description_snapshot
    add constraint fk_svd_stream_asset
        foreign key (media_stream_id, media_asset_id)
        references media_stream (id, media_asset_id);

-- 4. snapshot -> artifact ownership: (media_asset_id, artifact_id) MUST be an
--    existing media_asset_artifact link (rejects D2/D3/D5/D6: artifact of
--    another asset, unlinked artifact, nonexistent artifact)
alter table source_visual_description_snapshot
    add constraint fk_svd_asset_artifact
        foreign key (media_asset_id, artifact_id)
        references media_asset_artifact (media_asset_id, artifact_id);

-- D4 (nonexistent stream) remains rejected by the existing V5 FK
-- fk_source_visual_snapshot_stream -> media_stream(id).
