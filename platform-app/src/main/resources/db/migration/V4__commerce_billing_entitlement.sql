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
