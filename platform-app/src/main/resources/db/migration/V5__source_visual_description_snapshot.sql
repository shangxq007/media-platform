-- ROADMAP_18 CIP2 (CIP2A/B/C): durable canonical SourceVisualDescription snapshot,
-- Media-owned, bound to immutable source content (artifact content version).
-- canonical_payload is a deterministic lossless encoding (see
-- SourceVisualDescriptionCodec); reload NEVER re-runs provider/normalizer.

create table source_visual_description_snapshot (
    media_stream_id varchar(64) primary key,
    media_asset_id   varchar(64) not null,
    artifact_id      varchar(64) not null,
    canonical_payload text not null,
    created_at       timestamp not null default current_timestamp,
    constraint fk_source_visual_snapshot_stream
        foreign key (media_stream_id) references media_stream(id)
);

-- non-visual streams must not be forced to carry a snapshot; the FK + PK above
-- still guarantees one snapshot per visual stream (no orphans).
