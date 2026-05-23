-- Per-tenant LiteLLM virtual key (Phase 3 AI gateway); plaintext stored for MVP — prefer Vault in hardened prod.

create table if not exists tenant_litellm_virtual_key (
    tenant_id varchar(64) primary key,
    virtual_key varchar(512) not null,
    key_alias varchar(128),
    enabled boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists ix_tenant_litellm_key_enabled on tenant_litellm_virtual_key(enabled);
