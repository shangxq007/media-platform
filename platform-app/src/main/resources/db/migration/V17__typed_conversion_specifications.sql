-- Typed conversion foundation only. This table stores immutable declarative plans;
-- artifact and MediaAsset remain the authoritative artifact/ownership repositories.
create table if not exists conversion_specification (
    specification_id varchar(128) primary key,
    tenant_id varchar(128) not null,
    workspace_id varchar(128) not null,
    actor_id varchar(128) not null,
    source_artifact_ids jsonb not null,
    normalized_parameters jsonb not null,
    requested_contract_id varchar(160) not null,
    contract_version varchar(64) not null,
    authority_scope varchar(160) not null,
    entitlement_snapshot_reference varchar(160),
    quota_snapshot_reference varchar(160),
    deterministic_fingerprint varchar(64) not null,
    parent_lineage jsonb not null default '[]'::jsonb,
    created_by varchar(128) not null,
    created_at timestamptz not null,
    constraint uq_conversion_spec_scope_fingerprint unique (tenant_id, workspace_id, deterministic_fingerprint),
    constraint chk_conversion_spec_fingerprint check (deterministic_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint chk_conversion_spec_sources_array check (jsonb_typeof(source_artifact_ids) = 'array' and jsonb_array_length(source_artifact_ids) > 0),
    constraint chk_conversion_spec_parameters_object check (jsonb_typeof(normalized_parameters) = 'object'),
    constraint chk_conversion_spec_lineage_array check (jsonb_typeof(parent_lineage) = 'array')
);
create or replace function reject_conversion_specification_mutation() returns trigger language plpgsql as $$
begin raise exception 'conversion_specification is immutable'; end; $$;
drop trigger if exists conversion_specification_immutable on conversion_specification;
create trigger conversion_specification_immutable before update or delete on conversion_specification
for each row execute function reject_conversion_specification_mutation();
