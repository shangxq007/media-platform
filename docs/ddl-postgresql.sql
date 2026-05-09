-- =============================================================================
-- 非权威参考草案（NOT SOURCE OF TRUTH）
--
-- 实际库结构以 Flyway 为准：platform-app/src/main/resources/db/migration/
-- 见 docs/database-schema.md。请勿仅修改本文件并假设与运行环境一致。
-- =============================================================================

-- Core schemas
create schema if not exists core;
create schema if not exists render;
create schema if not exists notify;
create schema if not exists ai;
create schema if not exists ops;
create schema if not exists ext;
create schema if not exists billing;
create schema if not exists commerce;
create schema if not exists payment;
create schema if not exists entitlement;

-- Identity / access
create table if not exists core.tenants (
  id uuid primary key,
  tenant_key text not null unique,
  display_name text not null,
  status text not null default 'active',
  created_at timestamptz not null default now()
);

create table if not exists core.users (
  id uuid primary key,
  tenant_id uuid not null references core.tenants(id),
  email text not null,
  display_name text,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  unique (tenant_id, email)
);

create table if not exists core.api_keys (
  id uuid primary key,
  tenant_id uuid not null references core.tenants(id),
  owner_type text not null,
  owner_id text not null,
  key_prefix text not null,
  secret_ref text not null,
  status text not null default 'active',
  created_at timestamptz not null default now()
);

-- Config / secrets refs
create table if not exists core.config_namespaces (
  id uuid primary key,
  namespace_key text not null unique,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists core.config_items (
  id uuid primary key,
  namespace_id uuid not null references core.config_namespaces(id) on delete cascade,
  config_key text not null,
  config_type text not null,
  value_json jsonb not null,
  value_version int not null default 1,
  is_encrypted boolean not null default false,
  status text not null default 'active',
  updated_at timestamptz not null default now(),
  unique(namespace_id, config_key, value_version)
);

create table if not exists core.secret_bindings (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  binding_key text not null unique,
  provider text not null,
  secret_ref text not null,
  created_at timestamptz not null default now()
);

-- Prompt
create table if not exists ai.prompt_templates (
  id uuid primary key,
  template_code text not null,
  latest_version int not null default 1,
  description text,
  created_at timestamptz not null default now(),
  unique(template_code)
);

create table if not exists ai.prompt_versions (
  id uuid primary key,
  template_id uuid not null references ai.prompt_templates(id) on delete cascade,
  version_no int not null,
  provider_scope text,
  system_prompt text,
  user_prompt text not null,
  variables_schema jsonb,
  created_at timestamptz not null default now(),
  unique(template_id, version_no)
);

create table if not exists ai.prompt_execution_logs (
  id uuid primary key,
  template_code text not null,
  version_no int not null,
  provider text,
  model text,
  variables_json jsonb,
  trace_id text,
  created_at timestamptz not null default now()
);

-- Render and artifacts
create table if not exists render.render_jobs (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  project_id text not null,
  timeline_snapshot_id text not null,
  profile text not null,
  status text not null default 'queued',
  workflow_id text,
  error_code text,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  finished_at timestamptz
);

create table if not exists render.artifact_catalog (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  artifact_key text not null unique,
  artifact_type text not null,
  location_uri text not null,
  checksum_sha256 text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists render.artifact_relations (
  id uuid primary key,
  parent_artifact_id uuid not null references render.artifact_catalog(id) on delete cascade,
  child_artifact_id uuid not null references render.artifact_catalog(id) on delete cascade,
  relation_type text not null,
  created_at timestamptz not null default now()
);

-- Notification
create table if not exists notify.notification_events (
  id uuid primary key,
  event_key text not null unique,
  event_type text not null,
  event_version int not null,
  tenant_id uuid references core.tenants(id),
  payload jsonb not null,
  occurred_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table if not exists notify.notification_templates (
  id uuid primary key,
  template_code text not null,
  channel text not null,
  locale text not null default 'en',
  version int not null,
  subject_template text,
  body_template text not null,
  content_type text not null default 'text/plain',
  status text not null default 'active',
  created_at timestamptz not null default now(),
  unique(template_code, channel, locale, version)
);

create table if not exists notify.notification_deliveries (
  id uuid primary key,
  event_id uuid not null references notify.notification_events(id) on delete cascade,
  channel text not null,
  provider_code text not null,
  delivery_status text not null,
  attempt_count int not null default 0,
  request_payload jsonb,
  response_payload jsonb,
  next_retry_at timestamptz,
  last_error_code text,
  last_error_message text,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);

-- Outbox / audit / schedule / observability
create table if not exists ops.outbox_events (
  id uuid primary key,
  aggregate_type text not null,
  aggregate_id text not null,
  event_type text not null,
  event_version int not null,
  payload jsonb not null,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  published_at timestamptz
);

create table if not exists ops.audit_records (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  actor_type text not null,
  actor_id text,
  action text not null,
  resource_type text not null,
  resource_id text,
  payload jsonb,
  created_at timestamptz not null default now()
);

create table if not exists ops.schedules (
  id uuid primary key,
  schedule_code text not null unique,
  cron_expr text,
  fixed_delay_seconds int,
  handler_code text not null,
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists ops.telemetry_bookmarks (
  id uuid primary key,
  bookmark_type text not null,
  bookmark_key text not null unique,
  payload jsonb not null,
  created_at timestamptz not null default now()
);

-- Quota / billing
create table if not exists billing.quota_definitions (
  id uuid primary key,
  quota_code text not null unique,
  description text,
  unit text not null,
  created_at timestamptz not null default now()
);

create table if not exists billing.tenant_quotas (
  id uuid primary key,
  tenant_id uuid not null references core.tenants(id),
  quota_definition_id uuid not null references billing.quota_definitions(id),
  hard_limit numeric not null,
  soft_limit numeric,
  created_at timestamptz not null default now(),
  unique(tenant_id, quota_definition_id)
);

create table if not exists billing.usage_records (
  id uuid primary key,
  tenant_id uuid not null references core.tenants(id),
  quota_code text not null,
  quantity numeric not null,
  usage_time timestamptz not null,
  reference_type text,
  reference_id text
);

-- Extensions / sandbox / federation
create table if not exists ext.extensions (
  id uuid primary key,
  extension_code text not null unique,
  extension_type text not null,
  runtime text not null,
  version text not null,
  artifact_uri text,
  checksum text,
  permissions jsonb not null default '{}'::jsonb,
  timeout_ms int,
  status text not null default 'active',
  created_at timestamptz not null default now()
);

create table if not exists ext.extension_invocations (
  id uuid primary key,
  extension_code text not null,
  extension_version text not null,
  caller_module text not null,
  input_hash text,
  output_summary text,
  trace_id text,
  duration_ms bigint,
  exit_status text,
  created_at timestamptz not null default now()
);

create table if not exists ext.sandbox_jobs (
  id uuid primary key,
  sandbox_type text not null,
  script_ref text,
  status text not null,
  trace_id text,
  created_at timestamptz not null default now()
);

create table if not exists ext.federation_query_jobs (
  id uuid primary key,
  engine text not null,
  query_text text not null,
  status text not null,
  trace_id text,
  created_at timestamptz not null default now()
);


-- Commerce / payment / billing / entitlement
create table if not exists commerce.commerce_products (
  id uuid primary key,
  product_code text not null unique,
  purchase_mode text not null,
  feature_bundle_code text not null,
  quota_profile_code text,
  status text not null default 'active',
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists commerce.commerce_prices (
  id uuid primary key,
  product_id uuid not null references commerce.commerce_products(id) on delete cascade,
  price_code text not null unique,
  currency_code text not null,
  amount_minor bigint not null,
  billing_interval text,
  created_at timestamptz not null default now()
);

create table if not exists commerce.provider_product_mappings (
  id uuid primary key,
  provider_code text not null,
  external_product_ref text not null,
  external_price_ref text,
  product_id uuid not null references commerce.commerce_products(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(provider_code, external_product_ref, coalesce(external_price_ref, ''))
);

create table if not exists commerce.checkout_sessions (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  checkout_session_code text not null unique,
  product_id uuid not null references commerce.commerce_products(id),
  provider_code text,
  session_status text not null default 'created',
  success_url text,
  cancel_url text,
  created_at timestamptz not null default now(),
  expires_at timestamptz
);

create table if not exists commerce.purchase_orders (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  checkout_session_id uuid references commerce.checkout_sessions(id),
  canonical_product_code text not null,
  order_status text not null default 'pending',
  total_amount_minor bigint,
  currency_code text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists payment.payment_attempts (
  id uuid primary key,
  purchase_order_id uuid references commerce.purchase_orders(id),
  provider_code text not null,
  provider_reference text,
  attempt_status text not null,
  amount_minor bigint,
  currency_code text,
  request_payload jsonb,
  response_payload jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists payment.provider_webhook_events (
  id uuid primary key,
  provider_code text not null,
  webhook_event_key text not null unique,
  webhook_event_type text not null,
  webhook_event_version int not null default 1,
  signature_valid boolean not null default false,
  payload jsonb not null,
  occurred_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists billing.subscription_contracts (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  subject_type text not null,
  subject_id text not null,
  canonical_product_code text not null,
  provider_code text,
  external_contract_ref text,
  contract_state text not null,
  period_start_at timestamptz,
  period_end_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists billing.billing_invoices (
  id uuid primary key,
  contract_id uuid references billing.subscription_contracts(id),
  provider_code text,
  external_invoice_ref text,
  invoice_status text not null,
  amount_due_minor bigint,
  amount_paid_minor bigint,
  currency_code text,
  due_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists entitlement.feature_definitions (
  id uuid primary key,
  feature_code text not null unique,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists entitlement.feature_bundles (
  id uuid primary key,
  bundle_code text not null unique,
  description text,
  created_at timestamptz not null default now()
);

create table if not exists entitlement.feature_bundle_items (
  id uuid primary key,
  bundle_id uuid not null references entitlement.feature_bundles(id) on delete cascade,
  feature_id uuid not null references entitlement.feature_definitions(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(bundle_id, feature_id)
);

create table if not exists entitlement.entitlement_grants (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  subject_type text not null,
  subject_id text not null,
  bundle_code text not null,
  quota_profile_code text,
  source_type text not null,
  source_ref text,
  grant_status text not null default 'active',
  effective_at timestamptz not null default now(),
  expires_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists entitlement.entitlement_overrides (
  id uuid primary key,
  tenant_id uuid references core.tenants(id),
  subject_type text not null,
  subject_id text not null,
  override_kind text not null,
  override_payload jsonb not null,
  effective_at timestamptz not null default now(),
  expires_at timestamptz,
  created_at timestamptz not null default now()
);
