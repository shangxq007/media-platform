create table if not exists nlq_report_definition (
    report_id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    name varchar(255) not null,
    description clob,
    widgets_json clob,
    query_definitions_json clob,
    created_by varchar(128),
    visibility varchar(32) not null,
    schedule_json clob,
    created_at timestamp not null,
    updated_at timestamp not null,
    archived boolean not null default false
);

create table if not exists nlq_query_history (
    query_id varchar(64) primary key,
    user_id varchar(128) not null,
    tenant_id varchar(64),
    workspace_id varchar(64),
    question_redacted clob,
    sql_hash varchar(64),
    datasets_json clob,
    row_count int not null,
    duration_ms bigint not null,
    risk_level varchar(32),
    status varchar(32) not null,
    error_code varchar(64),
    created_at timestamp not null
);

create table if not exists nlq_report_execution (
    execution_id varchar(64) primary key,
    report_id varchar(64) not null,
    status varchar(32) not null,
    row_count int not null,
    duration_ms bigint not null,
    error_code varchar(64),
    created_at timestamp not null
);
