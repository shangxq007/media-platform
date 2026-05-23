-- L9: optional revision labels (JSON array) for History tagging / filters.

alter table timeline_revision add column if not exists labels_json varchar(512);

create index if not exists ix_timeline_revision_project_source
    on timeline_revision (project_id, source);
