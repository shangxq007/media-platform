-- Media asset probe metadata: persist ffprobe/media analysis results per asset.

create table if not exists media_asset_metadata (
    id                      varchar(64) primary key,
    tenant_id               varchar(64) not null,
    project_id              varchar(64) not null,
    asset_id                varchar(64) not null,
    asset_uri               varchar(1024) not null,
    valid                   boolean not null default false,
    container               varchar(32),
    file_size_bytes         bigint default 0,
    duration_ms             double default 0,
    width                   int default 0,
    height                  int default 0,
    fps                     double default 0,
    video_codec             varchar(64),
    audio_codec             varchar(64),
    audio_sample_rate       int default 0,
    audio_channels          int default 0,
    has_audio               boolean default false,
    rotation                int default 0,
    color_space             varchar(32),
    bitrate                 bigint default 0,
    is_vfr                  boolean default false,
    stream_count            int default 0,
    client_export_compatible boolean default false,
    normalize_required      boolean default true,
    warnings                varchar(4096),
    error_message           varchar(1024),
    probed_at               timestamp not null default current_timestamp
);

create index if not exists ix_mam_tenant_asset on media_asset_metadata(tenant_id, asset_id);
create index if not exists ix_mam_project on media_asset_metadata(project_id);
create index if not exists ix_mam_probed_at on media_asset_metadata(probed_at);
