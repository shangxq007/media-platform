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
create index idx_problematic_data_tenant on problematic_data_record(tenant_id);
create index idx_problematic_data_status on problematic_data_record(status);
create index idx_problematic_data_type on problematic_data_record(problematic_type);
create index idx_problematic_data_severity on problematic_data_record(severity);
create index idx_problematic_data_render_job on problematic_data_record(render_job_id);
create index idx_problematic_data_prompt_exec on problematic_data_record(prompt_execution_id);
create index idx_problematic_data_detected_at on problematic_data_record(detected_at);
create index idx_problematic_data_human_review on problematic_data_record(requires_human_review, status);

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
insert into problematic_data_rule_config (rule_id, rule_name, data_type, default_severity, description, auto_fixable, auto_fix_action, enabled) values
('RJB-001', 'Missing RenderJob Output', 'MISSING_FIELD', 'HIGH', 'RenderJob completed but has no output artifact', false, null, true),
('RJB-002', 'Stuck RenderJob', 'INVALID_STATE_TRANSITION', 'MEDIUM', 'RenderJob stuck in non-terminal state for too long', true, 'MARK_STALE_AND_RETRY', true),
('RJB-003', 'Duplicate RenderJob', 'DUPLICATE_ENTRY', 'LOW', 'Multiple render jobs with same project+profile+timeline hash', true, 'MARK_DUPLICATE', true),
('PMT-001', 'Prompt Sensitive Data Leak', 'MISSING_FIELD', 'CRITICAL', 'Sensitive prompt variable found in execution record', null, false, null, true),
('PMT-002', 'Prompt Output Mismatch', 'OUTPUT_MISMATCH', 'HIGH', 'Prompt execution output does not match expected format', false, null, true),
('PRV-001', 'Provider Error Spike', 'ERROR_RATE_SPIKE', 'HIGH', 'Provider error rate exceeds threshold in time window', false, null, true),
('WRK-001', 'Worker Stale Heartbeat', 'PERFORMANCE_ANOMALY', 'MEDIUM', 'Remote worker has not sent heartbeat within expected interval', true, 'MARK_WORKER_OFFLINE', true),
('SLA-001', 'SLA Breach', 'SLA_BREACH', 'CRITICAL', 'Render job exceeded SLA time limit', false, null, true),
('CST-001', 'Cost Anomaly', 'COST_ANOMALY', 'HIGH', 'Render job cost significantly exceeds estimated cost', false, null, true)
on conflict (rule_id) do nothing;
