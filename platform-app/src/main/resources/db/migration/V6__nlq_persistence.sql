-- NLQ report definitions, query history, and report executions (Prompt 64)

create table if not exists nlq_report_definition (
    report_id              varchar(64)  not null primary key,
    tenant_id              varchar(64),
    workspace_id           varchar(64),
    name                   varchar(255) not null,
    description            text,
    widgets_json           text,
    query_definitions_json text,
    created_by             varchar(128),
    visibility             varchar(32)  not null default 'PRIVATE',
    schedule_json          text,
    created_at             timestamp    not null default now(),
    updated_at             timestamp    not null default now(),
    archived               boolean      not null default false
);

create index if not exists ix_nlq_report_tenant on nlq_report_definition(tenant_id);
create index if not exists ix_nlq_report_workspace on nlq_report_definition(workspace_id);

create table if not exists nlq_query_history (
    query_id           varchar(64)  not null primary key,
    user_id            varchar(128) not null,
    tenant_id          varchar(64),
    workspace_id       varchar(64),
    question_redacted  text,
    sql_hash           varchar(64),
    datasets_json      text,
    row_count          int          not null default 0,
    duration_ms        bigint       not null default 0,
    risk_level         varchar(32),
    status             varchar(32)  not null,
    error_code         varchar(64),
    created_at         timestamp    not null default now()
);

create index if not exists ix_nlq_history_tenant on nlq_query_history(tenant_id);
create index if not exists ix_nlq_history_user on nlq_query_history(user_id);

create table if not exists nlq_report_execution (
    execution_id   varchar(64)  not null primary key,
    report_id      varchar(64)  not null,
    status         varchar(32)  not null,
    row_count      int          not null default 0,
    duration_ms    bigint       not null default 0,
    error_code     varchar(64),
    created_at     timestamp    not null default now()
);

create index if not exists ix_nlq_report_exec_report on nlq_report_execution(report_id);
