create table media_thumbnail_task (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(128) not null,
    source_asset_id varchar(64) not null,
    timestamp_seconds double precision not null,
    image_format varchar(16) not null,
    width integer,
    quality integer,
    idempotency_key varchar(256) not null,
    status varchar(32) not null,
    artifact_id varchar(128),
    failure_code varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_media_thumbnail_task_idempotency unique (tenant_id, project_id, idempotency_key)
);
create index ix_media_thumbnail_task_scope on media_thumbnail_task(tenant_id, project_id, created_at desc);
