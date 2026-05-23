-- Outbound delivery: reference secrets in Vault instead of inline JSON

alter table delivery_destination
    add column if not exists credential_ref varchar(512);

-- H2 (dev) does not support partial indexes; full index is sufficient for local preview.
create index if not exists ix_delivery_destination_credential_ref
    on delivery_destination(credential_ref);
