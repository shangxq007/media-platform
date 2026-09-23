create table if not exists composition_template_workflow_draft (
    tenant_id varchar(255) not null, workspace_id varchar(255) not null, workflow_id varchar(255) not null,
    version varchar(64) not null, revision bigint not null, lifecycle varchar(32) not null default 'DRAFT',
    definition jsonb not null, updated_at timestamp with time zone not null default now(),
    primary key (tenant_id, workspace_id, workflow_id)
);
create table if not exists composition_application_draft (
    tenant_id varchar(255) not null, workspace_id varchar(255) not null, application_id varchar(255) not null,
    version varchar(64) not null, revision bigint not null, lifecycle varchar(32) not null default 'DRAFT',
    definition jsonb not null, updated_at timestamp with time zone not null default now(),
    primary key (tenant_id, workspace_id, application_id)
);
create table if not exists composition_validation_snapshot (
    snapshot_id varchar(255) primary key, tenant_id varchar(255) not null, workspace_id varchar(255) not null,
    subject_id varchar(255) not null, result jsonb not null, created_at timestamp with time zone not null default now()
);
