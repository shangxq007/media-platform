create table cover_image_task (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(128) not null,
    subject_artifact_id varchar(128) not null,
    timestamp_seconds double precision not null,
    image_format varchar(16) not null,
    width integer,
    quality integer,
    idempotency_key varchar(256) not null,
    provider_id varchar(128) not null,
    provider_version varchar(32) not null,
    status varchar(32) not null,
    artifact_id varchar(128),
    failure_code varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uq_cover_image_task_idempotency unique (tenant_id, project_id, idempotency_key)
);
create index ix_cover_image_task_scope on cover_image_task(tenant_id, project_id, created_at desc);
