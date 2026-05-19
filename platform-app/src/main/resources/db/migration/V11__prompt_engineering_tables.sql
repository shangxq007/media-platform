-- V11__prompt_engineering_tables.sql
-- Prompt Engineering Platform database schema

-- Prompt templates
create table if not exists prompt_template (
    template_id varchar(64) primary key,
    name varchar(255) not null,
    description text,
    category varchar(128),
    tags text,
    owner varchar(128),
    status varchar(32) not null default 'DRAFT',
    schema_version varchar(32) not null default '1.0.0',
    current_prompt_version varchar(32),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

-- Prompt template versions
create table if not exists prompt_template_version (
    version_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    template_body text not null,
    variable_schema_json text,
    changelog text,
    created_by varchar(128),
    created_at timestamp not null default now(),
    checksum varchar(64),
    previous_version varchar(32),
    deprecated boolean not null default false,
    unique(template_id, prompt_version)
);

-- Prompt execution runs
create table if not exists prompt_execution_run (
    execution_id varchar(64) primary key,
    template_id varchar(64) not null references prompt_template(template_id),
    prompt_version varchar(32) not null,
    tenant_id varchar(64) not null,
    user_id varchar(128) not null,
    model_provider varchar(64),
    model_name varchar(64),
    rendered_prompt_hash varchar(64),
    redacted_prompt_preview varchar(512),
    input_variables_redacted_json text,
    output_summary text,
    status varchar(32) not null default 'PENDING',
    risk_level varchar(32) not null default 'LOW',
    token_estimate int not null default 0,
    cost_estimate double precision not null default 0,
    started_at timestamp not null default now(),
    finished_at timestamp,
    error_code varchar(64),
    error_details_json text,
    related_prompt_file varchar(256),
    related_manifest_entry varchar(256)
);

-- Prompt evaluation results
create table if not exists prompt_evaluation_result (
    evaluation_id varchar(64) primary key,
    execution_id varchar(64) not null references prompt_execution_run(execution_id),
    template_id varchar(64) not null,
    evaluator_user_id varchar(128) not null,
    acceptance_criteria_met boolean not null default false,
    documentation_updated boolean not null default false,
    manifest_updated boolean not null default false,
    tests_pass boolean not null default false,
    has_high_risk_changes boolean not null default false,
    has_human_review_items boolean not null default false,
    has_scope_creep boolean not null default false,
    has_false_claims boolean not null default false,
    overall_verdict varchar(32) not null,
    evaluated_at timestamp not null default now()
);

-- Indexes
create index idx_prompt_template_status on prompt_template(status);
create index idx_prompt_template_category on prompt_template(category);
create index idx_prompt_execution_template on prompt_execution_run(template_id);
create index idx_prompt_execution_tenant on prompt_execution_run(tenant_id);
create index idx_prompt_execution_status on prompt_execution_run(status);
create index idx_prompt_version_template on prompt_template_version(template_id);
