-- Client export session persistence: replace in-memory ConcurrentHashMap with database table.

create table if not exists client_export_session (
    id                  varchar(64) primary key,
    tenant_id           varchar(64) not null,
    workspace_id        varchar(64),
    project_id          varchar(64) not null,
    user_id             varchar(128),
    timeline_snapshot_id varchar(64),
    export_type         varchar(32)  not null default 'CLIENT_BROWSER',
    preset              varchar(64),
    status              varchar(32)  not null default 'CREATED',
    progress            int          not null default 0,
    resolution          varchar(32)  default '1280x720',
    fps                 int          default 30,
    format              varchar(16)  default 'webm',
    watermark_enabled   boolean      default true,
    video_bitrate       int,
    audio_bitrate       int,
    max_duration_sec    int,
    output_uri          varchar(512),
    artifact_id         varchar(64),
    download_path       varchar(512),
    error_code          varchar(64),
    error_message       varchar(1024),
    created_at          timestamp    not null default current_timestamp,
    updated_at          timestamp    not null default current_timestamp,
    expires_at          timestamp
);

create index if not exists ix_cex_tenant      on client_export_session(tenant_id);
create index if not exists ix_cex_project     on client_export_session(project_id);
create index if not exists ix_cex_status      on client_export_session(status);
create index if not exists ix_cex_tenant_proj on client_export_session(tenant_id, project_id);
