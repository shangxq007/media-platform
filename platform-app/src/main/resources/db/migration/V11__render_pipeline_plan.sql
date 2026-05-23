-- Pipeline DAG execution plan persisted per render job (Internal JSON → Render Plan)
alter table render_job add column if not exists pipeline_plan_json text;
alter table render_job add column if not exists pipeline_execution_json text;
