create table if not exists storage_object (
    id varchar(64) primary key,
    provider_code varchar(64) not null,
    bucket varchar(128) not null,
    object_key text not null,
    content_type varchar(255),
    checksum_sha256 varchar(128),
    file_size_bytes bigint,
    lifecycle_policy varchar(64),
    created_at timestamp not null
);

create table if not exists prompt_template (
    id varchar(64) primary key,
    template_code varchar(128) not null,
    locale varchar(32) not null,
    version int not null,
    template_body text not null,
    variables_schema text,
    status varchar(32) not null,
    created_at timestamp not null,
    unique(template_code, locale, version)
);

create table if not exists prompt_execution_log (
    id varchar(64) primary key,
    template_code varchar(128) not null,
    template_version int,
    provider_code varchar(64),
    model_code varchar(128),
    variables_json text,
    rendered_prompt text,
    created_at timestamp not null
);

create table if not exists cloud_resource_definition (
    id varchar(64) primary key,
    provider_code varchar(64) not null,
    resource_type varchar(64) not null,
    logical_name varchar(128) not null,
    spec_json text not null,
    status varchar(32) not null,
    created_at timestamp not null
);

create table if not exists secret_ref (
    id varchar(64) primary key,
    namespace_key varchar(128) not null,
    secret_key varchar(128) not null,
    backend_type varchar(64) not null,
    backend_ref varchar(255) not null,
    created_at timestamp not null,
    unique(namespace_key, secret_key)
);

create table if not exists extension_definition (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_type varchar(64) not null,
    language varchar(32),
    runtime varchar(64),
    version varchar(64) not null,
    artifact_uri text,
    status varchar(32) not null,
    timeout_ms bigint,
    config_schema text,
    created_at timestamp not null,
    unique(extension_code, version)
);

create table if not exists extension_invocation (
    id varchar(64) primary key,
    extension_code varchar(128) not null,
    extension_version varchar(64) not null,
    caller_module varchar(128) not null,
    input_summary text,
    output_summary text,
    exit_status varchar(32),
    duration_ms bigint,
    created_at timestamp not null
);

create table if not exists app_datasource (
    id varchar(64) primary key,
    datasource_code varchar(64) not null unique,
    datasource_kind varchar(32) not null,
    dialect varchar(32),
    jdbc_url text,
    secret_ref varchar(255),
    is_primary boolean not null,
    usage_role varchar(64) not null,
    created_at timestamp not null
);
