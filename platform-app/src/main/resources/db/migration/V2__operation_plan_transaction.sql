-- OPERATION_PLAN_TRANSACTION_MODEL_V1 (OPI1/OPI3):
-- authoritative per-project head/ref row enabling database-enforced CAS, plus
-- durable ApplyCommandId idempotency table. V1: single ref per project
-- (ref_id = 'main'); future RevisionCommand model may add refs.

create table timeline_revision_ref (
    project_id         varchar(64)  not null,
    ref_id             varchar(64)  not null,
    head_revision_id   varchar(64),
    version            bigint       not null default 0,
    updated_at         timestamp    not null default current_timestamp,
    primary key (project_id, ref_id)
);

-- head must reference an existing revision when set
alter table timeline_revision_ref
    add constraint fk_timeline_revision_ref_head
    foreign key (head_revision_id) references timeline_revision(id);

-- initialize authoritative head rows deterministically from the current
-- derived head (max revision_number per project). Greenfield/unshipped:
-- single canonical migration path, no dual-write fallback.
insert into timeline_revision_ref (project_id, ref_id, head_revision_id, version, updated_at)
select t.project_id, 'main', t.id, 0, t.created_at
from timeline_revision t
join (
    select project_id, max(revision_number) as max_rev
    from timeline_revision
    group by project_id
) m on m.project_id = t.project_id and m.max_rev = t.revision_number;

-- durable ApplyCommandId idempotency (OPI3): same command key always replays
-- the original durable result; unique key is the concurrency authority.
create table apply_command (
    apply_command_id     varchar(64)  not null,
    plan_digest          varchar(64)  not null,
    fingerprint          varchar(64)  not null,
    status               varchar(16)  not null,
    result_revision_id   varchar(64),
    result_content_hash  varchar(64),
    result_status        varchar(16),
    project_id           varchar(64),
    created_at           timestamp    not null default current_timestamp,
    completed_at         timestamp,
    primary key (apply_command_id)
);

create index ix_apply_command_fingerprint on apply_command(fingerprint);
