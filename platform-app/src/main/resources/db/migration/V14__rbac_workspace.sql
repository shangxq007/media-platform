create table if not exists workspace (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    plan_tier varchar(64) not null default 'FREE',
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    user_id varchar(64) not null,
    role varchar(64) not null,
    status varchar(32) not null default 'ACTIVE',
    joined_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists workspace_group (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    created_at timestamp not null
);

create table if not exists workspace_group_member (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    member_id varchar(64) not null,
    created_at timestamp not null
);

create table if not exists role (
    id varchar(64) primary key,
    role_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    scope varchar(32) not null,
    created_at timestamp not null
);

create table if not exists permission (
    id varchar(64) primary key,
    permission_key varchar(128) not null unique,
    name varchar(255) not null,
    description text,
    resource_type varchar(128),
    created_at timestamp not null
);

create table if not exists role_permission (
    id varchar(64) primary key,
    role_id varchar(64) not null,
    permission_id varchar(64) not null,
    created_at timestamp not null
);

create table if not exists user_role_assignment (
    id varchar(64) primary key,
    tenant_id varchar(64),
    workspace_id varchar(64),
    user_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_by varchar(64),
    created_at timestamp not null
);

create table if not exists group_role_assignment (
    id varchar(64) primary key,
    workspace_id varchar(64) not null,
    group_id varchar(64) not null,
    role_id varchar(64) not null,
    assigned_at timestamp not null
);

create table if not exists service_account (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    description text,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create table if not exists api_client (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    workspace_id varchar(64) not null,
    name varchar(255) not null,
    client_key_hash varchar(255) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null
);

create index if not exists ix_workspace_tenant_id on workspace(tenant_id);
create index if not exists ix_workspace_member_workspace_id on workspace_member(workspace_id);
create index if not exists ix_workspace_member_user_id on workspace_member(user_id);
create index if not exists ix_workspace_group_workspace_id on workspace_group(workspace_id);
create index if not exists ix_workspace_group_member_group_id on workspace_group_member(group_id);
create index if not exists ix_workspace_group_member_member_id on workspace_group_member(member_id);
create index if not exists ix_role_permission_role_id on role_permission(role_id);
create index if not exists ix_user_role_assignment_user_id on user_role_assignment(user_id);
create index if not exists ix_user_role_assignment_workspace_id on user_role_assignment(workspace_id);
create index if not exists ix_group_role_assignment_group_id on group_role_assignment(group_id);
create index if not exists ix_service_account_workspace_id on service_account(workspace_id);
create index if not exists ix_api_client_workspace_id on api_client(workspace_id);
