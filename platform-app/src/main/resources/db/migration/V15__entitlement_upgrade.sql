create table if not exists entitlement_bundle (
    id varchar(64) primary key,
    bundle_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    allowed_providers json,
    allowed_presets json,
    gpu_allowed boolean not null default false,
    remote_worker_allowed boolean not null default false,
    custom_fonts_allowed boolean not null default false,
    max_subtitle_tracks int not null default 2,
    max_concurrent_jobs int not null default 1,
    monthly_render_minutes bigint not null default 60,
    storage_limit_bytes bigint not null default 1073741824,
    watermark_required boolean not null default true,
    priority_queue_allowed boolean not null default false,
    beta_effects_allowed boolean not null default false,
    prompt_execution_limit bigint not null default 100,
    extension_execution_allowed boolean not null default false,
    api_access_allowed boolean not null default false,
    mcp_access_allowed boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists quota_profile (
    id varchar(64) primary key,
    profile_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    monthly_render_minutes bigint not default 60,
    daily_render_jobs int not null default 5,
    concurrent_render_jobs int not null default 1,
    storage_bytes bigint not null default 1073741824,
    gpu_minutes bigint not null default 0,
    remote_worker_jobs int not null default 0,
    prompt_executions bigint not null default 100,
    extension_executions bigint not null default 0,
    api_calls_per_minute int not null default 60,
    mcp_calls_per_minute int not null default 30,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_entitlement_pool (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    feature_key varchar(128) not null,
    total_quota bigint not null default 0,
    used_quota bigint not null default 0,
    period varchar(32) not null default 'MONTHLY',
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_member_entitlement_grant (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    member_id varchar(64) not null,
    feature_key varchar(128) not null,
    quota_amount bigint not null default 0,
    starts_at timestamp not null,
    expires_at timestamp,
    status varchar(32) not null default 'ACTIVE',
    granted_by varchar(64),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_quota_allocation (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    member_id varchar(64) not null,
    quota_profile_key varchar(128) not null,
    allocated_amount bigint not null default 0,
    used_amount bigint not null default 0,
    period varchar(32) not null default 'MONTHLY',
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists ix_entitlement_bundle_bundle_key on entitlement_bundle(bundle_key);
create index if not exists ix_entitlement_bundle_status on entitlement_bundle(status);
create index if not exists ix_quota_profile_profile_key on quota_profile(profile_key);
create index if not exists ix_workspace_entitlement_pool_workspace_id on workspace_entitlement_pool(workspace_id);
create index if not exists ix_workspace_entitlement_pool_feature_key on workspace_entitlement_pool(feature_key);
create index if not exists ix_workspace_member_grant_workspace_id on workspace_member_entitlement_grant(workspace_id);
create index if not exists ix_workspace_member_grant_member_id on workspace_member_entitlement_grant(member_id);
create index if not exists ix_workspace_member_grant_status on workspace_member_entitlement_grant(status);
create index if not exists ix_workspace_quota_alloc_workspace_id on workspace_quota_allocation(workspace_id);
create index if not exists ix_workspace_quota_alloc_member_id on workspace_quota_allocation(member_id);

alter table entitlement_override add column if not exists status varchar(32) not null default 'ACTIVE';
alter table entitlement_override add column if not exists created_at timestamp;
alter table entitlement_override add column if not exists updated_at timestamp;
