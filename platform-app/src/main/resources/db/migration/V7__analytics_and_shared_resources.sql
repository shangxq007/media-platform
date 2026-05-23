-- User analytics persistence (Prompt 19–20) and shared resource grants (Prompt 68)

create table if not exists user_behavior_event (
    event_id        varchar(64)  not null primary key,
    tenant_id       varchar(64)  not null,
    user_id         varchar(128) not null,
    event_type      varchar(64)  not null,
    action          varchar(128),
    resource_type   varchar(64),
    resource_id     varchar(128),
    metadata_json   text,
    occurred_at     timestamp    not null default now()
);

create index if not exists ix_user_behavior_tenant on user_behavior_event(tenant_id);
create index if not exists ix_user_behavior_user on user_behavior_event(user_id);
create index if not exists ix_user_behavior_occurred on user_behavior_event(occurred_at);

create table if not exists user_profile (
    profile_id                  varchar(64)  not null primary key,
    tenant_id                   varchar(64)  not null,
    user_id                     varchar(128) not null,
    display_name                varchar(255),
    preferred_languages_json    text,
    feature_usage_counts_json   text,
    action_counts_json          text,
    total_sessions              int          not null default 0,
    total_actions               int          not null default 0,
    first_seen_at               timestamp,
    last_active_at              timestamp,
    updated_at                  timestamp    not null default now(),
    unique(tenant_id, user_id)
);

create index if not exists ix_user_profile_tenant on user_profile(tenant_id);

create table if not exists user_segment (
    segment_id      varchar(64)  not null primary key,
    tenant_id       varchar(64)  not null,
    name            varchar(255) not null,
    description     text,
    criteria_json   text,
    user_ids_json   text,
    user_count      int          not null default 0,
    computed_at     timestamp    not null default now()
);

create index if not exists ix_user_segment_tenant on user_segment(tenant_id);

create table if not exists user_habits (
    tenant_id       varchar(64)  not null,
    user_id         varchar(128) not null,
    habits_json     text         not null,
    computed_at     timestamp    not null default now(),
    primary key (tenant_id, user_id)
);

create table if not exists shared_resource_grant (
    grant_id              varchar(64)  not null primary key,
    tenant_id             varchar(64)  not null,
    resource_type         varchar(32)  not null,
    resource_id           varchar(128) not null,
    resource_name         varchar(255),
    resource_description  text,
    resource_status       varchar(32),
    shared_by_user_id     varchar(128),
    shared_with_user_id   varchar(128) not null,
    permission            varchar(32)  not null default 'READ',
    status                varchar(32)  not null default 'ACTIVE',
    created_at            timestamp    not null default now(),
    expires_at            timestamp
);

create index if not exists ix_shared_resource_grant_recipient
    on shared_resource_grant(tenant_id, shared_with_user_id);
create index if not exists ix_shared_resource_grant_resource
    on shared_resource_grant(resource_type, resource_id);
