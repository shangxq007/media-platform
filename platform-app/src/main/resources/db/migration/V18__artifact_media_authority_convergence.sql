-- Artifact is the sole identity and authorization authority.
-- This forward-only migration preserves legacy rows before retiring the MediaAsset root.

alter table artifact add column if not exists workspace_id varchar(128);
alter table artifact add column if not exists storage_reference text;
alter table artifact add column if not exists provenance jsonb not null default '{}'::jsonb;
alter table artifact add column if not exists source_lineage jsonb not null default '[]'::jsonb;
alter table artifact add column if not exists conversion_specification_id varchar(128);
alter table artifact add column if not exists idempotency_key varchar(256);
update artifact set workspace_id = coalesce(nullif(project_id, ''), 'legacy') where workspace_id is null;
alter table artifact alter column workspace_id set not null;
alter table artifact add constraint uq_artifact_scope_idempotency unique (tenant_id, workspace_id, idempotency_key);

create table if not exists artifact_media_details (
    artifact_id varchar(64) primary key references artifact(id) on delete restrict,
    subtype varchar(64),
    container varchar(128),
    mime_type varchar(128) not null,
    codec varchar(128),
    duration_millis bigint,
    width integer,
    height integer,
    frame_rate varchar(64),
    tracks jsonb not null default '[]'::jsonb,
    color_space varchar(128),
    sample_rate integer,
    channel_layout varchar(128),
    keyframe_index_reference text,
    thumbnail_artifact_id varchar(64) references artifact(id) on delete restrict,
    legacy_media_asset_id varchar(64) unique,
    constraint chk_artifact_media_duration check (duration_millis is null or duration_millis >= 0),
    constraint chk_artifact_media_dimensions check ((width is null or width > 0) and (height is null or height > 0)),
    constraint chk_artifact_media_sample_rate check (sample_rate is null or sample_rate > 0),
    constraint chk_artifact_media_tracks_array check (jsonb_typeof(tracks) = 'array')
);

-- Existing Artifact links win. Unlinked MediaAsset rows retain their original id when free.
insert into artifact (id, tenant_id, project_id, workspace_id, content_digest, byte_length,
                      media_type, artifact_kind, state, schema_version, storage_reference, created_at)
select coalesce(link.artifact_id, m.id), m.tenant_id, m.project_id, m.project_id,
       coalesce(nullif(m.checksum, ''), 'legacy-media:' || m.id), coalesce(m.size_bytes, 0),
       case when lower(m.media_type) like 'video/%' then 'VIDEO'
            when lower(m.media_type) like 'audio/%' then 'AUDIO'
            when lower(m.media_type) like 'image/%' then 'IMAGE' else 'BINARY' end,
       'SOURCE_MEDIA', 'AVAILABLE', 1, m.storage_key, m.created_at
from media_asset m
left join lateral (select maa.artifact_id from media_asset_artifact maa
                   where maa.media_asset_id = m.id order by maa.created_at limit 1) link on true
where not exists (select 1 from artifact a where a.id = coalesce(link.artifact_id, m.id));

insert into artifact_media_details (artifact_id, subtype, container, mime_type, duration_millis,
                                    legacy_media_asset_id)
select coalesce(link.artifact_id, m.id), m.classification, null, m.media_type, null, m.id
from media_asset m
left join lateral (select maa.artifact_id from media_asset_artifact maa
                   where maa.media_asset_id = m.id order by maa.created_at limit 1) link on true
on conflict (artifact_id) do update set legacy_media_asset_id = excluded.legacy_media_asset_id;

-- Legacy tables remain read-only historical storage for dependent facts; they no longer define identity.
alter table media_asset rename to media_asset_retired;
alter table media_asset_artifact rename to artifact_legacy_media_link;
create or replace function reject_retired_media_asset_mutation() returns trigger language plpgsql as $$
begin raise exception 'MediaAsset runtime authority retired; use Artifact'; end; $$;
drop trigger if exists media_asset_retired_immutable on media_asset_retired;
create trigger media_asset_retired_immutable before insert or update or delete on media_asset_retired
for each statement execute function reject_retired_media_asset_mutation();
