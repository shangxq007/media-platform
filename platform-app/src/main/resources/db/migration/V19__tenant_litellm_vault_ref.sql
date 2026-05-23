-- Vault reference for per-tenant LiteLLM virtual keys (plaintext virtual_key optional when vault_ref set).

alter table tenant_litellm_virtual_key add column if not exists vault_ref varchar(512);

alter table tenant_litellm_virtual_key alter column virtual_key drop not null;

create index if not exists ix_tenant_litellm_vault_ref on tenant_litellm_virtual_key(vault_ref);
