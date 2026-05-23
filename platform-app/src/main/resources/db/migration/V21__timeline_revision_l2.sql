-- L2: persisted RFC6902 patch ops + edit-session indexing

alter table timeline_revision add column if not exists patch_ops_json text;

create index if not exists ix_timeline_revision_edit_session
    on timeline_revision (project_id, edit_session_id, created_at desc);
