create table if not exists prompt_template (
    template_id varchar(64) primary key,
    name varchar(255) not null,
    description clob,
    category varchar(128),
    tags clob,
    owner varchar(128),
    status varchar(32) not null default 'DRAFT',
    schema_version varchar(32) not null default '1.0.0',
    current_prompt_version varchar(32),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists prompt_template_version (
    version_id varchar(64) primary key,
    template_id varchar(64) not null,
    prompt_version varchar(32) not null,
    template_body clob not null,
    variable_schema_json clob,
    changelog clob,
    created_by varchar(128),
    created_at timestamp not null,
    checksum varchar(64),
    previous_version varchar(32),
    deprecated boolean not null default false
);

create table if not exists prompt_execution_run (
    execution_id varchar(64) primary key,
    template_id varchar(64) not null,
    prompt_version varchar(32) not null,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    model_provider varchar(64),
    model_name varchar(64),
    rendered_prompt_hash varchar(64),
    redacted_prompt_preview varchar(512),
    input_variables_redacted_json clob,
    output_summary clob,
    status varchar(32) not null,
    risk_level varchar(32) not null,
    token_estimate int not null,
    cost_estimate double not null,
    started_at timestamp not null,
    finished_at timestamp,
    error_code varchar(64),
    error_details_json clob,
    related_prompt_file varchar(256),
    related_manifest_entry varchar(256)
);
