-- Render artifact delivery (outbound to customer storage)

create table if not exists delivery_destination (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(64),
    name varchar(255) not null,
    protocol varchar(32) not null,
    config_json text,
    credential_json text,
    enabled boolean default true,
    verified_at timestamp,
    created_at timestamp not null
);

create index if not exists ix_delivery_destination_tenant on delivery_destination(tenant_id);

create table if not exists delivery_policy (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64),
    destination_id varchar(64) not null,
    artifact_selector varchar(32) not null default 'FINAL_ONLY',
    path_template varchar(512) not null,
    trigger_mode varchar(16) not null default 'AUTO',
    enabled boolean default true,
    created_at timestamp not null
);

create index if not exists ix_delivery_policy_tenant_project on delivery_policy(tenant_id, project_id);

create table if not exists delivery_job (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    project_id varchar(64) not null,
    render_job_id varchar(64) not null,
    destination_id varchar(64) not null,
    status varchar(32) not null,
    source_uri varchar(1024) not null,
    remote_path varchar(1024),
    remote_uri varchar(1024),
    bytes_transferred bigint,
    attempt_count int not null default 0,
    error_code varchar(64),
    error_message varchar(2048),
    created_at timestamp not null,
    completed_at timestamp
);

create index if not exists ix_delivery_job_render on delivery_job(render_job_id);
create index if not exists ix_delivery_job_status on delivery_job(status);
