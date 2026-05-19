-- =============================================================================
-- V13: Extension Platform Upgrade
--
-- Purpose: Upgrade dynamic extension platform with multi-layer trust model,
--          routing/canary release, resource limits, SPI context, structured
--          results, rollback tracking, and comprehensive audit.
--
-- Changes:
--   1. trust_level column on extension_definition
--   2. extension_routing_rule table for canary/cohort routing
--   3. extension_resource_limit table for per-extension quotas
--   4. extension_rollback_point table for rollback snapshots
--   5. extension_audit_event table for detailed audit trail
--   6. extension_invocation enhancements (trace_id, trust_level, resource_usage)
--   7. Indexes for new tables
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Trust level on extension_definition
-- ---------------------------------------------------------------------------
alter table if exists extension_definition add column if not exists trust_level varchar(32) not null default 'SEMI_TRUSTED';
alter table if exists extension_definition add column if not exists sandboxed boolean not null default true;
alter table if exists extension_definition add column if not exists max_concurrency int not null default 4;
alter table if exists extension_definition add column if not exists max_memory_mb int not null default 256;
alter table if exists extension_definition add column if not exists max_cpu_percent int not null default 50;
alter table if exists extension_definition add column if not exists max_queue_size int not null default 100;
alter table if exists extension_definition add column if not exists max_input_bytes bigint not null default 10485760;
alter table if exists extension_definition add column if not exists max_output_bytes bigint not null default 4194304;
alter table if exists extension_definition add column if not exists requires_review boolean not null default false;
alter table if exists extension_definition add column if not exists review_status varchar(32) default 'APPROVED';

-- ---------------------------------------------------------------------------
-- 2. Extension routing rules for canary / cohort-based traffic splitting
-- ---------------------------------------------------------------------------
create table if not exists extension_routing_rule (
    id varchar(64) primary key,
    rule_name varchar(255) not null,
    extension_code varchar(128) not null,
    source_version varchar(64),
    target_version varchar(64) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    scene varchar(128),
    priority int not null default 0,
    traffic_percent int not null default 0,
    enabled boolean not null default true,
    created_at timestamp not null default now(),
    created_by varchar(128),
    updated_at timestamp,
    unique(extension_code, source_version, target_version, tenant_id, user_id, scene)
);

create index if not exists ix_ext_routing_extension_code on extension_routing_rule(extension_code);
create index if not exists ix_ext_routing_tenant on extension_routing_rule(tenant_id);
create index if not exists ix_ext_routing_enabled on extension_routing_rule(enabled);
create index if not exists ix_ext_routing_priority on extension_routing_rule(priority);

-- ---------------------------------------------------------------------------
-- 3. Per-extension resource limits (overrides defaults from extension_definition)
-- ---------------------------------------------------------------------------
create table if not exists extension_resource_limit (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    tenant_id varchar(64),
    limit_type varchar(64) not null,
    max_value bigint not null,
    current_value bigint not null default 0,
    window_seconds int not null default 60,
    created_at timestamp not null default now(),
    updated_at timestamp,
    unique(extension_code, tenant_id, limit_type)
);

create index if not exists ix_ext_res_limit_code on extension_resource_limit(extension_code);
create index if not exists ix_ext_res_limit_tenant on extension_resource_limit(tenant_id);

-- ---------------------------------------------------------------------------
-- 4. Rollback point snapshots
-- ---------------------------------------------------------------------------
create table if not exists extension_rollback_point (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    version varchar(64) not null,
    artifact_uri text,
    config_snapshot text,
    routing_rule_ids text,
    created_at timestamp not null default now(),
    created_by varchar(128),
    is_active boolean not null default true
);

create index if not exists ix_ext_rollback_code on extension_rollback_point(extension_code);
create index if not exists ix_ext_rollback_active on extension_rollback_point(is_active);

-- ---------------------------------------------------------------------------
-- 5. Detailed extension audit events
-- ---------------------------------------------------------------------------
create table if not exists extension_audit_event (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_version varchar(64),
    event_type varchar(64) not null,
    actor varchar(128) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    trace_id varchar(128),
    trust_level varchar(32),
    details text,
    severity varchar(32) not null default 'INFO',
    created_at timestamp not null default now()
);

create index if not exists ix_ext_audit_code on extension_audit_event(extension_code);
create index if not exists ix_ext_audit_type on extension_audit_event(event_type);
create index if not exists ix_ext_audit_tenant on extension_audit_event(tenant_id);
create index if not exists ix_ext_audit_trace on extension_audit_event(trace_id);
create index if not exists ix_ext_audit_created on extension_audit_event(created_at);

-- ---------------------------------------------------------------------------
-- 6. Enhance extension_invocation with trace_id, trust_level, resource_usage
-- ---------------------------------------------------------------------------
alter table if exists extension_invocation add column if not exists trace_id varchar(128);
alter table if exists extension_invocation add column if not exists trust_level varchar(32);
alter table if exists extension_invocation add column if not exists input_bytes bigint;
alter table if exists extension_invocation add column if not exists output_bytes bigint;
alter table if exists extension_invocation add column if not exists cpu_time_ms bigint;
alter table if exists extension_invocation add column if not exists memory_peak_mb bigint;
alter table if exists extension_invocation add column if not exists routing_rule_id varchar(64);

create index if not exists ix_ext_invocation_trace on extension_invocation(trace_id);

-- ---------------------------------------------------------------------------
-- 7. Sandbox execution jobs table
-- ---------------------------------------------------------------------------
create table if not exists sandbox_execution_job (
    id varchar(64) primary key,
    extension_code varchar(128),
    language varchar(32) not null,
    script_hash varchar(64),
    status varchar(32) not null default 'PENDING',
    trace_id varchar(128),
    tenant_id varchar(64),
    user_id varchar(128),
    timeout_ms bigint not null default 30000,
    started_at timestamp,
    finished_at timestamp,
    exit_code int,
    output_preview text,
    error_message text,
    created_at timestamp not null default now()
);

create index if not exists ix_sandbox_job_status on sandbox_execution_job(status);
create index if not exists ix_sandbox_job_trace on sandbox_execution_job(trace_id);
create index if not exists ix_sandbox_job_extension on sandbox_execution_job(extension_code);
