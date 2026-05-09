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
