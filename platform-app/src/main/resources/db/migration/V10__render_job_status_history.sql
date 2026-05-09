-- =============================================================================
-- V10: Render Job Status History Table
--
-- Purpose: Track every state transition of render jobs for auditability,
--          debugging, and failure compensation.
-- =============================================================================

create table if not exists render_job_status_history (
    id varchar(64) primary key,
    job_id varchar(64) not null,
    from_status varchar(30),
    to_status varchar(30) not null,
    reason varchar(255),
    error_code varchar(100),
    occurred_at timestamp not null default now()
);

create index if not exists ix_rjsh_job_id on render_job_status_history(job_id);
