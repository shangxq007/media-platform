-- Tenant subscription tier for EntitlementPolicyService (Prompt 06, 59)

create table if not exists tenant_entitlement_tier (
    tenant_id   varchar(64)  not null primary key,
    tier        varchar(32)  not null default 'FREE',
    updated_at  timestamp    not null default now()
);

create index if not exists ix_tenant_entitlement_tier_tier on tenant_entitlement_tier(tier);
