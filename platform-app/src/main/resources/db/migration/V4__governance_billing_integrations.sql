-- Merged schema: entitlement, navigation, billing models, notifications, social


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
    monthly_render_minutes bigint not null default 60,
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


-- =============================================================================
-- V16: Navigation & Dynamic Routing
--
-- Purpose: Store frontend route definitions and navigation policies so that
--          the backend can evaluate visibility / enablement per user context.
-- =============================================================================

create table if not exists frontend_route_definition (
    id              varchar(64)  not null primary key,
    route_key       varchar(128) not null unique,
    path            varchar(256) not null,
    component_key   varchar(128) not null,
    title           varchar(256) not null,
    description     text,
    menu_group      varchar(128),
    icon            varchar(64),
    sort_order      int          not null default 0,
    parent_route_key varchar(128),
    required_permissions    text,
    required_roles          text,
    required_entitlements   text,
    required_tier           varchar(64),
    required_features       text,
    supported_sources       text,
    visible         boolean      not null default true,
    enabled         boolean      not null default true,
    hidden_reason   varchar(512),
    disabled_reason varchar(512),
    upgrade_options text,
    created_at      timestamp    not null default now(),
    updated_at      timestamp    not null default now()
);

create index if not exists ix_route_def_menu_group on frontend_route_definition(menu_group);
create index if not exists ix_route_def_visible on frontend_route_definition(visible);
create index if not exists ix_route_def_enabled on frontend_route_definition(enabled);
create index if not exists ix_route_def_parent on frontend_route_definition(parent_route_key);

create table if not exists navigation_policy (
    id              varchar(64)  not null primary key,
    policy_key      varchar(128) not null unique,
    route_key       varchar(128) not null,
    policy_type     varchar(32)  not null,
    condition_expr  text         not null,
    effect          varchar(16)  not null,
    reason_code     varchar(128) not null,
    reason_message  text         not null,
    upgrade_options text,
    priority        int          not null default 0,
    enabled         boolean      not null default true,
    created_at      timestamp    not null default now(),
    updated_at      timestamp    not null default now(),
    constraint fk_nav_policy_route foreign key (route_key)
        references frontend_route_definition (route_key)
        on delete cascade
);

create index if not exists ix_nav_policy_route on navigation_policy(route_key);
create index if not exists ix_nav_policy_priority on navigation_policy(priority);


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



-- =============================================================================
-- Notification platform (catalog, delivery, inbox)
-- =============================================================================
create table if not exists notification_event_definition (
    id varchar(64) primary key,
    event_key varchar(100) not null unique,
    name varchar(200) not null,
    description varchar(500),
    category varchar(50) not null,
    severity varchar(20) not null,
    visibility varchar(30) not null,
    user_configurable boolean not null default false,
    critical boolean not null default false,
    default_enabled boolean not null default true,
    supported_channels text,
    required_permissions text,
    required_entitlements text,
    feature_flag_key varchar(100),
    novu_workflow_id varchar(100),
    local_template_key varchar(100),
    archived boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists notification_channel_binding (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    channel_type varchar(32) not null,
    destination_masked varchar(255),
    destination_encrypted text,
    verified boolean not null default false,
    verification_status varchar(32) not null default 'PENDING',
    enabled boolean not null default true,
    failure_count int not null default 0,
    last_failure_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists notification_subscription (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    event_key varchar(100) not null,
    enabled boolean not null default true,
    channels text,
    frequency varchar(30) not null default 'IMMEDIATE',
    filters text,
    quiet_hours_start varchar(10),
    quiet_hours_end varchar(10),
    quiet_hours_timezone varchar(50),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists notification_preference (
    id varchar(64) primary key,
    tenant_id varchar(64),
    user_id varchar(64) not null,
    event_key varchar(100) not null,
    enabled boolean not null default true,
    channels text,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique(tenant_id, user_id, event_key)
);

create table if not exists notification_delivery_record (
    id varchar(64) primary key,
    event_key varchar(100) not null,
    tenant_id varchar(64),
    user_id varchar(64),
    channel_type varchar(32) not null,
    status varchar(32) not null,
    attempts int not null default 0,
    payload_redacted text,
    provider_message_id varchar(128),
    error_code varchar(64),
    sent_at timestamp,
    failed_at timestamp,
    created_at timestamp not null
);

create table if not exists notification_user_inbox (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    event_key varchar(100),
    type varchar(32) not null default 'INFO',
    title varchar(255),
    message text,
    read boolean not null default false,
    link varchar(512),
    actor_id varchar(64),
    resource_type varchar(64),
    resource_id varchar(64),
    created_at timestamp not null,
    read_at timestamp
);

create index if not exists ix_notification_channel_binding_user on notification_channel_binding(user_id);
create index if not exists ix_notification_subscription_user on notification_subscription(user_id);
create index if not exists ix_notification_delivery_record_user on notification_delivery_record(user_id);
create index if not exists ix_notification_user_inbox_user on notification_user_inbox(user_id);

-- Feature flags (policy governance persistence)
create table if not exists feature_flag_definition (
    id varchar(64) primary key,
    flag_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    flag_type varchar(32) not null default 'BOOLEAN',
    enabled boolean not null default false,
    default_value_json text,
    variants_json text,
    tags_json text,
    owner varchar(128),
    archived boolean not null default false,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table if not exists feature_flag_targeting_rule (
    id varchar(64) primary key,
    flag_key varchar(128) not null,
    rule_id varchar(64),
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64),
    role varchar(64),
    tier varchar(64),
    percentage double precision,
    priority int not null default 0,
    enabled boolean not null default true,
    rule_json text not null,
    created_at timestamp not null default now()
);

create index if not exists ix_feature_flag_targeting_flag on feature_flag_targeting_rule(flag_key);

-- =============================================================================
-- Social publish
-- =============================================================================
create table if not exists social_connected_platform (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    platform_type varchar(32) not null,
    platform_user_id varchar(256),
    platform_username varchar(256),
    access_token_encrypted text,
    refresh_token_encrypted text,
    token_expires_at timestamp,
    status varchar(16) default 'ACTIVE',
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table if not exists social_post (
    id varchar(36) primary key,
    tenant_id varchar(36) not null,
    user_id varchar(36) not null,
    content_text text,
    media_urls varchar(4000),
    platform_type varchar(32) not null,
    status varchar(16) default 'DRAFT',
    platform_post_id varchar(256),
    platform_post_url varchar(512),
    scheduled_at timestamp,
    published_at timestamp,
    failed_at timestamp,
    error_code varchar(64),
    error_message text,
    retry_count int default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table if not exists social_post_analytics (
    id varchar(36) primary key,
    post_id varchar(36) not null,
    platform_type varchar(32) not null,
    impressions int default 0,
    reach int default 0,
    likes int default 0,
    comments int default 0,
    shares int default 0,
    clicks int default 0,
    fetched_at timestamp,
    created_at timestamp not null default now()
);

create index if not exists ix_social_post_tenant_user on social_post(tenant_id, user_id);
create index if not exists ix_social_post_status on social_post(status);
create index if not exists ix_social_connected_platform_tenant_user on social_connected_platform(tenant_id, user_id);
create index if not exists ix_social_post_analytics_post on social_post_analytics(post_id);
