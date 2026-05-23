create table if not exists credit_wallet (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64),
    balance_minor bigint not null default 0,
    currency_code varchar(8) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists credit_transaction (
    id varchar(64) primary key,
    wallet_id varchar(64) not null,
    transaction_type varchar(32) not null,
    amount_minor bigint not null,
    balance_after_minor bigint not null,
    reference_type varchar(64),
    reference_id varchar(64),
    description clob,
    created_at timestamp not null
);

create table if not exists billing_ledger_entry (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64),
    entry_type varchar(32) not null,
    amount_minor bigint not null,
    currency_code varchar(8) not null,
    reference_type varchar(64),
    reference_id varchar(64),
    description clob,
    created_at timestamp not null
);
