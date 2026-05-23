-- Merged schema: prompt, extensions, workspace RBAC


-- V11__prompt_engineering_tables.sql
-- Prompt Engineering Platform database schema

-- Prompt templates
create table if not exists prompt_template (
    template_id varchar(64) primary key,
    name varchar(255) not null,
    description text,
    category varchar(128),
    tags text,
    owner varchar(128),
    status varchar(32) not null default 'DRAFT',
    schema_version varchar(32) not null default '1.0.0',
    current_prompt_version varchar(32),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

-- Prompt template versions
create table if not exists prompt_template_version (
    version_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    template_body text not null,
    variable_schema_json text,
    changelog text,
    created_by varchar(128),
    created_at timestamp not null default now(),
    checksum varchar(64),
    previous_version varchar(32),
    deprecated boolean not null default false,
    unique(template_id, prompt_version)
);

-- Prompt execution runs
create table if not exists prompt_execution_run (
    execution_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    model_provider varchar(64),
    model_name varchar(64),
    rendered_prompt_hash varchar(64),
    redacted_prompt_preview varchar(512),
    input_variables_redacted_json text,
    output_summary text,
    status varchar(32) not null default 'PENDING',
    risk_level varchar(32) not null default 'LOW',
    token_estimate int not null default 0,
    cost_estimate double precision not null default 0,
    started_at timestamp not null default now(),
    finished_at timestamp,
    error_code varchar(64),
    error_details_json text,
    related_prompt_file varchar(256),
    related_manifest_entry varchar(256)
);

-- Prompt evaluation results
create table if not exists prompt_evaluation_result (
    evaluation_id varchar(64) primary key,
    execution_id varchar(64) not null references prompt_execution_run(execution_id),
    template_id varchar(64) not null,
    evaluator_user_id varchar(128) not null,
    acceptance_criteria_met boolean not null default false,
    documentation_updated boolean not null default false,
    manifest_updated boolean not null default false,
    tests_pass boolean not null default false,
    has_high_risk_changes boolean not null default false,
    has_human_review_items boolean not null default false,
    has_scope_creep boolean not null default false,
    has_false_claims boolean not null default false,
    overall_verdict varchar(32) not null,
    evaluated_at timestamp not null default now()
);

-- Indexes
create index if not exists idx_prompt_template_status on prompt_template(status);
create index if not exists idx_prompt_execution_template on prompt_execution_run(template_id);
create index if not exists idx_prompt_execution_tenant on prompt_execution_run(tenant_id);
create index if not exists idx_prompt_execution_status on prompt_execution_run(status);
create index if not exists idx_prompt_version_template on prompt_template_version(template_id);


-- V12__problematic_data_tables.sql
-- Problematic data detection and handling schema

-- Main problematic data records table
create table if not exists problematic_data_record (
    record_id varchar(64) primary key,
    data_type varchar(64) not null,
    data_id varchar(128) not null,
    tenant_id varchar(64),
    user_id varchar(128),
    problematic_type varchar(64) not null,
    severity varchar(32) not null default 'MEDIUM',
    detection_rule varchar(64),
    description text,
    context_json text,
    source_session_id varchar(128),
    render_job_id varchar(128),
    prompt_execution_id varchar(128),
    provider_key varchar(64),
    worker_id varchar(64),
    status varchar(32) not null default 'DETECTED',
    auto_fix_applied text,
    quarantine_table varchar(128),
    requires_human_review boolean not null default false,
    human_review_notes text,
    detected_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128)
);

-- Indexes for common queries
create index if not exists idx_problematic_data_tenant on problematic_data_record(tenant_id);
create index if not exists idx_problematic_data_status on problematic_data_record(status);
create index if not exists idx_problematic_data_type on problematic_data_record(problematic_type);
create index if not exists idx_problematic_data_severity on problematic_data_record(severity);
create index if not exists idx_problematic_data_render_job on problematic_data_record(render_job_id);
create index if not exists idx_problematic_data_prompt_exec on problematic_data_record(prompt_execution_id);
create index if not exists idx_problematic_data_detected_at on problematic_data_record(detected_at);
create index if not exists idx_problematic_data_human_review on problematic_data_record(requires_human_review, status);

-- Quarantine table for render jobs
create table if not exists quarantined_render_jobs (
    quarantine_id varchar(64) primary key,
    original_job_id varchar(128) not null,
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

-- Quarantine table for prompt executions
create table if not exists quarantined_prompt_executions (
    quarantine_id varchar(64) primary key,
    original_execution_id varchar(128) not null,
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

-- Quarantine table for provider/worker issues
create table if not exists quarantined_provider_workers (
    quarantine_id varchar(64) primary key,
    provider_key varchar(64),
    worker_id varchar(64),
    tenant_id varchar(64),
    quarantine_reason varchar(64) not null,
    detection_rule varchar(64),
    original_data_json text,
    status varchar(32) not null default 'QUARANTINED',
    quarantined_at timestamp not null default now(),
    resolved_at timestamp,
    resolved_by varchar(128),
    resolution_notes text
);

-- Detection rules configuration
create table if not exists problematic_data_rule_config (
    rule_id varchar(64) primary key,
    rule_name varchar(255) not null,
    data_type varchar(64) not null,
    default_severity varchar(32) not null default 'MEDIUM',
    description text,
    detection_query text,
    auto_fixable boolean not null default false,
    auto_fix_action varchar(255),
    enabled boolean not null default true,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

-- Insert default detection rules
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('RJB-001', 'Missing RenderJob Output', 'MISSING_FIELD', 'HIGH', 'RenderJob completed but has no output artifact', false, '', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('RJB-002', 'Stuck RenderJob', 'INVALID_STATE_TRANSITION', 'MEDIUM', 'RenderJob stuck in non-terminal state for too long', true, 'MARK_STALE_AND_RETRY', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('RJB-003', 'Duplicate RenderJob', 'DUPLICATE_ENTRY', 'LOW', 'Multiple render jobs with same project+profile+timeline hash', true, 'MARK_DUPLICATE', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('PMT-001', 'Prompt Sensitive Data Leak', 'MISSING_FIELD', 'CRITICAL', 'Sensitive prompt variable found in execution record', false, '', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('PMT-002', 'Prompt Output Mismatch', 'OUTPUT_MISMATCH', 'HIGH', 'Prompt execution output does not match expected format', false, '', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('PRV-001', 'Provider Error Spike', 'ERROR_RATE_SPIKE', 'HIGH', 'Provider error rate exceeds threshold in time window', false, '', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('WRK-001', 'Worker Stale Heartbeat', 'PERFORMANCE_ANOMALY', 'MEDIUM', 'Remote worker has not sent heartbeat within expected interval', true, 'MARK_WORKER_OFFLINE', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('SLA-001', 'SLA Breach', 'SLA_BREACH', 'CRITICAL', 'Render job exceeded SLA time limit', false, '', true);
merge into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) key(rule_id) values ('CST-001', 'Cost Anomaly', 'COST_ANOMALY', 'HIGH', 'Render job cost significantly exceeds estimated cost', false, '', true);


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


create table if not exists workspace (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    plan_tier varchar(64) not null default 'FREE',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    user_id varchar(64) not null,
    role varchar(64) not null,
    status varchar(32) not null default 'ACTIVE',
    joined_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_group (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    created_at timestamp not null
);

create table if not exists workspace_group_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    member_id varchar(64) not null,
    created_at timestamp not null
);

create table if not exists role (
    id varchar(64) primary key,
    role_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    scope varchar(32) not null,
    created_at timestamp not null
);

create table if not exists permission (
    id varchar(64) primary key,
    permission_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    resource_type varchar(128),
    created_at timestamp not null
);

create table if not exists role_permission (
    id varchar(64) primary key,
    role_id varchar(64) not null,
    permission_id varchar(64) not null,
    created_at timestamp not null
);

create table if not exists user_role_assignment (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_by varchar(64),
    created_at timestamp not null
);

create table if not exists group_role_assignment (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_at timestamp not null
);

create table if not exists service_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create table if not exists api_client (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    client_key_hash varchar(255) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index if not exists ix_workspace_tenant_id on workspace(tenant_id);
create index if not exists ix_workspace_member_workspace_id on workspace_member(workspace_id);
create index if not exists ix_workspace_member_user_id on workspace_member(user_id);
create index if not exists ix_workspace_group_workspace_id on workspace_group(workspace_id);
create index if not exists ix_workspace_group_member_group_id on workspace_group_member(group_id);
create index if not exists ix_workspace_group_member_member_id on workspace_group_member(member_id);
create index if not exists ix_role_permission_role_id on role_permission(role_id);
create index if not exists ix_user_role_assignment_user_id on user_role_assignment(user_id);
create index if not exists ix_user_role_assignment_workspace_id on user_role_assignment(workspace_id);
create index if not exists ix_group_role_assignment_group_id on group_role_assignment(group_id);
create index if not exists ix_service_account_workspace_id on service_account(workspace_id);
create index if not exists ix_api_client_workspace_id on api_client(workspace_id);
