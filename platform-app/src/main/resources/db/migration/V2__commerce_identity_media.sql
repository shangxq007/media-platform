-- Merged schema: commerce, identity, media


create table if not exists commerce_product (
  id varchar(64) primary key,
  product_code varchar(128) not null unique,
  purchase_mode varchar(64) not null,
  feature_bundle_code varchar(128) not null,
  quota_profile_code varchar(128),
  status varchar(32) not null,
  created_at timestamp not null
);

create table if not exists commerce_price (
  id varchar(64) primary key,
  product_id varchar(64) not null,
  price_code varchar(128) not null unique,
  currency_code varchar(8) not null,
  amount_minor bigint not null,
  billing_interval varchar(32),
  created_at timestamp not null
);

create table if not exists provider_product_mapping (
  id varchar(64) primary key,
  provider_code varchar(64) not null,
  external_product_ref varchar(255) not null,
  external_price_ref varchar(255),
  product_id varchar(64) not null,
  created_at timestamp not null
);

create table if not exists checkout_session (
  id varchar(64) primary key,
  checkout_session_code varchar(128) not null unique,
  product_id varchar(64) not null,
  provider_code varchar(64),
  session_status varchar(32) not null,
  success_url text,
  cancel_url text,
  created_at timestamp not null
);

create table if not exists purchase_order (
  id varchar(64) primary key,
  checkout_session_id varchar(64),
  canonical_product_code varchar(128) not null,
  order_status varchar(32) not null,
  total_amount_minor bigint,
  currency_code varchar(8),
  created_at timestamp not null
);

create table if not exists payment_attempt (
  id varchar(64) primary key,
  purchase_order_id varchar(64),
  provider_code varchar(64) not null,
  provider_reference varchar(255),
  attempt_status varchar(32) not null,
  amount_minor bigint,
  currency_code varchar(8),
  request_payload text,
  response_payload text,
  created_at timestamp not null
);

create table if not exists provider_webhook_event (
  id varchar(64) primary key,
  provider_code varchar(64) not null,
  webhook_event_key varchar(255) not null unique,
  webhook_event_type varchar(128) not null,
  webhook_event_version int not null,
  signature_valid boolean not null,
  payload text not null,
  created_at timestamp not null
);

create table if not exists subscription_contract (
  id varchar(64) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  canonical_product_code varchar(128) not null,
  provider_code varchar(64),
  external_contract_ref varchar(255),
  contract_state varchar(32) not null,
  period_start_at timestamp,
  period_end_at timestamp,
  created_at timestamp not null
);

create table if not exists billing_invoice (
  id varchar(64) primary key,
  contract_id varchar(64),
  provider_code varchar(64),
  external_invoice_ref varchar(255),
  invoice_status varchar(32) not null,
  amount_due_minor bigint,
  amount_paid_minor bigint,
  currency_code varchar(8),
  created_at timestamp not null
);

create table if not exists feature_definition (
  id varchar(64) primary key,
  feature_code varchar(128) not null unique,
  description varchar(255),
  created_at timestamp not null
);

create table if not exists feature_bundle (
  id varchar(64) primary key,
  bundle_code varchar(128) not null unique,
  description varchar(255),
  created_at timestamp not null
);

create table if not exists feature_bundle_item (
  id varchar(64) primary key,
  bundle_id varchar(64) not null,
  feature_id varchar(64) not null,
  created_at timestamp not null
);

create table if not exists entitlement_grant (
  id varchar(64) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  bundle_code varchar(128) not null,
  quota_profile_code varchar(128),
  source_type varchar(32) not null,
  source_ref varchar(255),
  grant_status varchar(32) not null,
  effective_at timestamp not null,
  expires_at timestamp
);

create table if not exists entitlement_override (
  id varchar(64) primary key,
  subject_type varchar(32) not null,
  subject_id varchar(128) not null,
  override_kind varchar(64) not null,
  override_payload text not null,
  effective_at timestamp not null,
  expires_at timestamp
);


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


-- =============================================================================
-- V8: Quota Usage & Render History Tables
--
-- Purpose: Persist quota usage tracking and render job history that were
--          previously stored in-memory via ConcurrentHashMap.
-- =============================================================================

-- =============================================================================
-- Quota Usage Table
-- =============================================================================

create table if not exists quota_usage (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    feature_code varchar(80) not null,
    usage_value int not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists ix_quota_usage_tenant_id on quota_usage(tenant_id);
create index if not exists ix_quota_usage_tenant_feature on quota_usage(tenant_id, feature_code);


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
