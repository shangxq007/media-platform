-- Incremental render: optional prior job whose artifacts / timeline revision are reused
alter table render_job add column if not exists base_job_id varchar(64);
create index if not exists ix_render_job_base_job_id on render_job(base_job_id);
