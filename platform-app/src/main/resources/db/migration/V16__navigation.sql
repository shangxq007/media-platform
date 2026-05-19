-- =============================================================================
-- V16: Navigation & Dynamic Routing
--
-- Purpose: Store frontend route definitions and navigation policies so that
--          the backend can evaluate visibility / enablement per user context.
-- =============================================================================

create table if not exists frontend_route_definition (
    id              varchar(64)  not null primary key,
    route_key       varchar(128) not null unique,
    path            varchar(256) not null,
    component_key   varchar(128) not null,
    title           varchar(256) not null,
    description     text,
    menu_group      varchar(128),
    icon            varchar(64),
    sort_order      int          not null default 0,
    parent_route_key varchar(128),
    required_permissions    text,
    required_roles          text,
    required_entitlements   text,
    required_tier           varchar(64),
    required_features       text,
    supported_sources       text,
    visible         boolean      not null default true,
    enabled         boolean      not null default true,
    hidden_reason   varchar(512),
    disabled_reason varchar(512),
    upgrade_options text,
    created_at      timestamp    not null default now(),
    updated_at      timestamp    not null default now()
);

create index if not exists ix_route_def_menu_group on frontend_route_definition(menu_group);
create index if not exists ix_route_def_visible on frontend_route_definition(visible);
create index if not exists ix_route_def_enabled on frontend_route_definition(enabled);
create index if not exists ix_route_def_parent on frontend_route_definition(parent_route_key);

create table if not exists navigation_policy (
    id              varchar(64)  not null primary key,
    policy_key      varchar(128) not null unique,
    route_key       varchar(128) not null,
    policy_type     varchar(32)  not null,
    condition_expr  text         not null,
    effect          varchar(16)  not null,
    reason_code     varchar(128) not null,
    reason_message  text         not null,
    upgrade_options text,
    priority        int          not null default 0,
    enabled         boolean      not null default true,
    created_at      timestamp    not null default now(),
    updated_at      timestamp    not null default now(),
    constraint fk_nav_policy_route foreign key (route_key)
        references frontend_route_definition (route_key)
        on delete cascade
);

create index if not exists ix_nav_policy_route on navigation_policy(route_key);
create index if not exists ix_nav_policy_priority on navigation_policy(priority);
