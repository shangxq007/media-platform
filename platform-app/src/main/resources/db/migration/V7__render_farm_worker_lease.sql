-- ============================================================
-- RENDER FARM LEVEL 3 MVP: Worker Registry + Job Lease
-- ============================================================

-- render_worker: persistent worker registry
create table render_worker (
    id varchar(64) primary key,
    worker_id varchar(128) not null unique,
    worker_type varchar(32) not null default 'RENDER',
    status varchar(32) not null default 'STARTING',
    version varchar(64),
    image_tag varchar(128),
    hostname varchar(256),
    zone varchar(64),
    provider_ids text,
    capabilities_json text,
    max_concurrent_jobs int not null default 1,
    active_job_count int not null default 0,
    cpu_cores int,
    memory_mb int,
    gpu_count int not null default 0,
    gpu_type varchar(64),
    disk_free_mb bigint,
    last_heartbeat_at timestamp not null,
    registered_at timestamp not null,
    expires_at timestamp,
    metadata_json text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index ix_render_worker_status on render_worker(status);
create index ix_render_worker_heartbeat on render_worker(last_heartbeat_at);
create index ix_render_worker_type on render_worker(worker_type);

-- render_job_lease: job-level lease for worker dispatch
create table render_job_lease (
    id varchar(64) primary key,
    lease_id varchar(128) not null unique,
    job_id varchar(64) not null,
    tenant_id varchar(64) not null,
    worker_id varchar(128) not null,
    provider_id varchar(64),
    status varchar(32) not null default 'CLAIMED',
    lease_version bigint not null default 1,
    claimed_at timestamp not null,
    lease_until timestamp not null,
    renewed_at timestamp,
    released_at timestamp,
    attempt int not null default 1,
    max_attempts int not null default 3,
    heartbeat_token_hash varchar(128),
    failure_reason text,
    failure_error_code varchar(64),
    created_by_scheduler varchar(64),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index ix_lease_job_id on render_job_lease(job_id);
create index ix_lease_worker_id on render_job_lease(worker_id);
create index ix_lease_status on render_job_lease(status);
create index ix_lease_until on render_job_lease(lease_until);
