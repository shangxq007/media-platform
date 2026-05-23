-- Tenant-scoped commerce checkout and carts; subscription tenant column for authoritative queries.

alter table checkout_session add column if not exists tenant_id varchar(64);
alter table checkout_session add column if not exists user_id varchar(128);
alter table checkout_session add column if not exists cart_id varchar(64);

create index if not exists ix_checkout_session_tenant on checkout_session(tenant_id);

alter table purchase_order add column if not exists tenant_id varchar(64);

create index if not exists ix_purchase_order_tenant on purchase_order(tenant_id);

create table if not exists commerce_cart (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    user_id varchar(128),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists ix_commerce_cart_tenant on commerce_cart(tenant_id);

create table if not exists commerce_cart_line (
    id varchar(64) primary key,
    cart_id varchar(64) not null,
    product_code varchar(128) not null,
    quantity int not null,
    created_at timestamp not null,
    unique(cart_id, product_code)
);

create index if not exists ix_commerce_cart_line_cart on commerce_cart_line(cart_id);

alter table subscription_contract add column if not exists tenant_id varchar(64);

create index if not exists ix_subscription_contract_tenant on subscription_contract(tenant_id);
