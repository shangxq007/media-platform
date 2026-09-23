create table platform_execution_admission (
    execution_id varchar(128) primary key,
    tenant_id varchar(128) not null,
    workspace_id varchar(128) not null,
    actor_id varchar(128) not null,
    source_domain varchar(64) not null,
    composition_id varchar(128) not null,
    composition_revision bigint not null check (composition_revision >= 0),
    plan_id varchar(256) not null,
    idempotency_key varchar(256) not null,
    request_hash varchar(128) not null,
    ownership_generation bigint not null default 0 check (ownership_generation >= 0),
    state varchar(32) not null check (state in ('ADMITTED','RUNNING','RETRYING','COMPLETED','FAILED','CANCELLED')),
    quota_units bigint not null check (quota_units >= 0),
    quota_charged boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_platform_execution_admission_idempotency unique (tenant_id, workspace_id, idempotency_key),
    constraint uq_platform_execution_admission_plan unique (tenant_id, workspace_id, plan_id)
);

create table platform_execution_result (
    execution_id varchar(128) not null references platform_execution_admission(execution_id),
    attempt_id varchar(128) not null,
    ownership_generation bigint not null check (ownership_generation >= 0),
    result_id varchar(256) not null,
    storage_receipt varchar(256) not null,
    artifact_id varchar(256) not null,
    media_asset_id varchar(256),
    output_digest varchar(256),
    output_length bigint,
    source_revision varchar(256),
    materialization_state varchar(32) not null check (materialization_state in ('STORAGE_ISSUED','ARTIFACT_COMMITTED','MEDIA_REGISTERED','FAILED','COMPENSATING')),
    reconciliation_state varchar(32) not null check (reconciliation_state in ('NONE','PENDING','RESOLVED','DEAD_LETTER')),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (execution_id, attempt_id),
    constraint uq_platform_execution_result_id unique (result_id)
);

create index ix_platform_execution_admission_scope_state on platform_execution_admission (tenant_id, workspace_id, state);
