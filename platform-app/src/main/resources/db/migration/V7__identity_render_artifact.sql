-- =============================================================================
-- V7: Identity, Render Job Enhancements, Artifact & Notification Tables
--
-- Purpose: Add tenant/project/user/api_key management tables, enhance
--          render_job with AI script and artifact tracking, add artifact
--          and notification_record tables for the end-to-end business flow.
-- =============================================================================

-- =============================================================================
-- Identity & Access Tables
-- =============================================================================

create table if not exists tenant (
    id varchar(64) primary key,
    name varchar(255) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create table if not exists project (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index if not exists ix_project_tenant_id on project(tenant_id);

create table if not exists "user" (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    username varchar(128) not null,
    email varchar(255) not null,
    role varchar(32) not null default 'MEMBER',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index if not exists ix_user_tenant_id on "user"(tenant_id);

create table if not exists api_key (
    id varchar(64) primary key,
    tenant_id varchar(64),
    fingerprint varchar(32) not null,
    hashed_key varchar(128) not null unique,
    principal varchar(255) not null,
    created_at timestamp not null,
    last_used_at timestamp,
    revoked_at timestamp
);

create index if not exists ix_api_key_fingerprint on api_key(fingerprint);

-- =============================================================================
-- Render Job Enhancements
-- =============================================================================

alter table if exists render_job add column if not exists ai_script text;
alter table if exists render_job add column if not exists artifact_uri text;
alter table if exists render_job add column if not exists error_message text;
alter table if exists render_job add column if not exists tenant_id varchar(64);

-- =============================================================================
-- Artifact Table
-- =============================================================================

create table if not exists artifact (
    id varchar(64) primary key,
    render_job_id varchar(64) not null,
    project_id varchar(64) not null,
    storage_uri text not null,
    format varchar(32),
    resolution varchar(32),
    duration bigint,
    created_at timestamp not null
);

create index if not exists ix_artifact_render_job_id on artifact(render_job_id);
create index if not exists ix_artifact_project_id on artifact(project_id);

-- =============================================================================
-- Notification Record Table
-- =============================================================================

create table if not exists notification_record (
    id varchar(64) primary key,
    event_id varchar(64) not null,
    channel varchar(32) not null,
    provider_code varchar(64) not null,
    status varchar(32) not null,
    subject varchar(512),
    body text,
    metadata_json text,
    attempt_count int not null default 1,
    created_at timestamp not null
);

create index if not exists ix_notification_record_event_id on notification_record(event_id);
create index if not exists ix_notification_record_status on notification_record(status);
