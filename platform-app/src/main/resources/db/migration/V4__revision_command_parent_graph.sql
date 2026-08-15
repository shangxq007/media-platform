-- REVISION_COMMAND_MODEL_V1 (RCI2/RCI3/RCI4):
-- 1) ordered revision-parent edge table = SINGLE graph-parent authority (RCI3)
-- 2) per-project revision-number counter = DB-safe allocation (RCI2)
-- 3) database-enforced same-project parent integrity (RCI4)
-- 4) legacy parent fields retire as graph authority (no dual write)

-- composite FK target: (project_id, id) must be unique BEFORE the RCI4 parent FK
create unique index ux_timeline_revision_project_id on timeline_revision(project_id, id);

-- ordered parent edges: normal edit/restore = one edge order 0; merge = two edges 0/1
create table timeline_revision_parent (
    project_id         varchar(64) not null,
    revision_id        varchar(64) not null,
    parent_revision_id varchar(64) not null,
    parent_order       int         not null,
    primary key (revision_id, parent_order),
    constraint ux_timeline_revision_parent_pair
        unique (revision_id, parent_revision_id),
    constraint ck_timeline_revision_parent_order_nonnegative
        check (parent_order >= 0),
    constraint ck_timeline_revision_parent_no_self
        check (revision_id <> parent_revision_id),
    constraint fk_timeline_revision_parent_revision
        foreign key (revision_id) references timeline_revision(id),
    -- RCI4: parent must reference an existing revision of the SAME project.
    -- Composite FK (project_id, parent_revision_id) -> timeline_revision(project_id, id)
    -- enforces same-project at the database level (no generic disabled-FK workaround).
    constraint fk_timeline_revision_parent_parent
        foreign key (project_id, parent_revision_id)
        references timeline_revision(project_id, id)
);

create index ix_timeline_revision_parent_child on timeline_revision_parent(revision_id);
create index ix_timeline_revision_parent_parent on timeline_revision_parent(parent_revision_id);

-- migrate existing single-parent history into ordered edges (order 0), preserving
-- every existing parent meaning. Root revisions get zero edges. No content/hash change.
insert into timeline_revision_parent (project_id, revision_id, parent_revision_id, parent_order)
select t.project_id, t.id, t.parent_revision_id, 0
from timeline_revision t
where t.parent_revision_id is not null;

-- per-project revision-number counter (RCI2): atomic UPDATE ... RETURNING allocation
create table project_revision_counter (
    project_id          varchar(64) not null primary key,
    next_revision_number bigint not null
);

insert into project_revision_counter (project_id, next_revision_number)
select project_id, coalesce(max(revision_number), 0) + 1
from timeline_revision
group by project_id;

-- new timeline_revision rows must carry the allocated number; existing unique
-- (project_id, revision_number) constraint remains the collision guard.

-- RCI idempotency domain separation: existing OperationPlan records map to
-- OPERATION_PLAN; RevisionCommand records use REVISION_COMMAND. PK uniqueness
-- plus domain column prevents semantic cross-domain replay.
alter table apply_command add column command_domain varchar(32) not null default 'OPERATION_PLAN';
