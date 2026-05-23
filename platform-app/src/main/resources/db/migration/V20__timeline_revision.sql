-- Domain timeline version control: revision chain per project (parent pointer + snapshot blob).

create table if not exists timeline_revision (
    id                  varchar(64)  primary key,
    project_id          varchar(64)  not null,
    tenant_id           varchar(64),
    parent_revision_id  varchar(64),
    revision_number     int          not null,
    snapshot_id         varchar(64)  not null,
    internal_revision   int          not null default 0,
    content_hash        varchar(64)  not null,
    schema_version      varchar(32)  not null default 'internal-1.0',
    source              varchar(32)  not null,
    author_user_id      varchar(64),
    edit_session_id     varchar(64),
    message             varchar(512),
    change_summary_json text,
    created_at          timestamp    not null
);

create unique index if not exists ux_timeline_revision_project_num
    on timeline_revision (project_id, revision_number);

create index if not exists ix_timeline_revision_project_created
    on timeline_revision (project_id, created_at desc);

create index if not exists ix_timeline_revision_parent
    on timeline_revision (parent_revision_id);

create index if not exists ix_timeline_revision_snapshot
    on timeline_revision (snapshot_id);

-- Optional linkage from snapshot to revision head at save time
alter table timeline_snapshot add column if not exists content_hash varchar(64);
alter table timeline_snapshot add column if not exists revision_number int;
