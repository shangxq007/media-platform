-- =============================================================================
-- V17: Flexible Multi-Billing Models
--
-- Purpose: Add tables for subscription plans, pricing rules, usage metering,
--          rating, billing ledger, credit wallets, invoice line items,
--          custom pricing rules, and discount policies.
-- =============================================================================

create table if not exists pricing_rule (
    id                varchar(64)  not null primary key,
    rule_key          varchar(128) not null unique,
    name              varchar(255) not null,
    description       text,
    pricing_model     varchar(32)  not null,
    meter_key         varchar(128),
    unit_price_minor  bigint,
    currency_code     varchar(8),
    tier_config       text,
    status            varchar(32)  not null default 'ACTIVE',
    effective_from    timestamp,
    effective_to      timestamp,
    created_at        timestamp    not null default now(),
    updated_at        timestamp    not null default now()
);

create index if not exists ix_pricing_rule_key on pricing_rule(rule_key);
create index if not exists ix_pricing_rule_model on pricing_rule(pricing_model);
create index if not exists ix_pricing_rule_status on pricing_rule(status);

create table if not exists subscription_plan (
    id                 varchar(64)  not null primary key,
    plan_key           varchar(128) not null unique,
    name               varchar(255) not null,
    description        text,
    billing_interval   varchar(32)  not null,
    base_price_minor   bigint       not null,
    currency_code      varchar(8)   not null,
    included_quota     text,
    status             varchar(32)  not null default 'ACTIVE',
    created_at         timestamp    not null default now(),
    updated_at         timestamp    not null default now()
);

create index if not exists ix_subscription_plan_key on subscription_plan(plan_key);
create index if not exists ix_subscription_plan_status on subscription_plan(status);

alter table if exists subscription_contract
    add column if not exists plan_key varchar(128);

alter table if exists subscription_contract
    add column if not exists included_quota_used text;

create table if not exists usage_meter (
    id                 varchar(64)  not null primary key,
    meter_key          varchar(128) not null unique,
    name               varchar(255) not null,
    description        text,
    unit               varchar(64)  not null,
    aggregation_type   varchar(32)  not null,
    status             varchar(32)  not null default 'ACTIVE',
    created_at         timestamp    not null default now()
);

create index if not exists ix_usage_meter_key on usage_meter(meter_key);

create table if not exists usage_record (
    id                varchar(64)  not null primary key,
    tenant_id         varchar(64),
    workspace_id      varchar(64),
    user_id           varchar(64),
    meter_key         varchar(128) not null,
    quantity          double precision not null,
    unit              varchar(64)  not null,
    recorded_at       timestamp    not null,
    idempotency_key   varchar(255) unique,
    created_at        timestamp    not null default now()
);

create index if not exists ix_usage_record_tenant on usage_record(tenant_id);
create index if not exists ix_usage_record_meter on usage_record(meter_key);
create index if not exists ix_usage_record_recorded on usage_record(recorded_at);

create table if not exists rated_usage_record (
    id                   varchar(64)  not null primary key,
    usage_record_id      varchar(64)  not null,
    pricing_rule_id      varchar(64)  not null,
    rated_amount_minor   bigint       not null,
    currency_code        varchar(8)   not null,
    rating_details       text,
    created_at           timestamp    not null default now()
);

create index if not exists ix_rated_usage_record_usage on rated_usage_record(usage_record_id);
create index if not exists ix_rated_usage_record_rule on rated_usage_record(pricing_rule_id);

create table if not exists billing_ledger_entry (
    id                varchar(64)  not null primary key,
    tenant_id         varchar(64),
    workspace_id      varchar(64),
    user_id           varchar(64),
    entry_type        varchar(32)  not null,
    amount_minor      bigint       not null,
    currency_code     varchar(8)   not null,
    reference_type    varchar(64),
    reference_id      varchar(64),
    description       text,
    created_at        timestamp    not null default now()
);

create index if not exists ix_billing_ledger_tenant on billing_ledger_entry(tenant_id);
create index if not exists ix_billing_ledger_type on billing_ledger_entry(entry_type);
create index if not exists ix_billing_ledger_ref on billing_ledger_entry(reference_type, reference_id);
create index if not exists ix_billing_ledger_created on billing_ledger_entry(created_at);

create table if not exists credit_wallet (
    id             varchar(64)  not null primary key,
    tenant_id      varchar(64),
    workspace_id   varchar(64),
    user_id        varchar(64),
    balance_minor  bigint       not null default 0,
    currency_code  varchar(8)   not null,
    status         varchar(32)  not null default 'ACTIVE',
    created_at     timestamp    not null default now(),
    updated_at     timestamp    not null default now()
);

create index if not exists ix_credit_wallet_tenant on credit_wallet(tenant_id);
create index if not exists ix_credit_wallet_status on credit_wallet(status);

create table if not exists credit_transaction (
    id                   varchar(64)  not null primary key,
    wallet_id            varchar(64)  not null,
    transaction_type     varchar(32)  not null,
    amount_minor         bigint       not null,
    balance_after_minor  bigint       not null,
    reference_type       varchar(64),
    reference_id         varchar(64),
    description          text,
    created_at           timestamp    not null default now()
);

create index if not exists ix_credit_txn_wallet on credit_transaction(wallet_id);
create index if not exists ix_credit_txn_type on credit_transaction(transaction_type);
create index if not exists ix_credit_txn_created on credit_transaction(created_at);

create table if not exists invoice_line_item (
    id                varchar(64)  not null primary key,
    invoice_id        varchar(64)  not null,
    line_type         varchar(32)  not null,
    description       text,
    quantity          double precision,
    unit_price_minor  bigint,
    amount_minor      bigint       not null,
    currency_code     varchar(8)   not null,
    period_start      timestamp,
    period_end        timestamp,
    created_at        timestamp    not null default now()
);

create index if not exists ix_invoice_line_item_invoice on invoice_line_item(invoice_id);

create table if not exists custom_pricing_rule (
    id                   varchar(64)  not null primary key,
    tenant_id            varchar(64),
    workspace_id         varchar(64),
    meter_key            varchar(128) not null,
    override_price_minor bigint,
    discount_percent     double precision,
    effective_from       timestamp,
    effective_to         timestamp,
    status               varchar(32)  not null default 'ACTIVE',
    created_at           timestamp    not null default now()
);

create index if not exists ix_custom_pricing_tenant on custom_pricing_rule(tenant_id);
create index if not exists ix_custom_pricing_meter on custom_pricing_rule(meter_key);

create table if not exists discount_policy (
    id                varchar(64)  not null primary key,
    policy_key        varchar(128) not null unique,
    name              varchar(255) not null,
    description       text,
    discount_type     varchar(32)  not null,
    discount_value    double precision not null,
    conditions        text,
    status            varchar(32)  not null default 'ACTIVE',
    effective_from    timestamp,
    effective_to      timestamp,
    created_at        timestamp    not null default now()
);

create index if not exists ix_discount_policy_key on discount_policy(policy_key);
create index if not exists ix_discount_policy_status on discount_policy(status);
