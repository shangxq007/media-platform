-- Render owns the immutable graph execution fence and the accepted final output.
-- Artifact remains the content/replica owner; no physical storage coordinates here.
alter table render_job add constraint uq_render_job_execution_scope unique (tenant_id,project_id,id);
alter table artifact add constraint uq_artifact_render_output_scope unique (tenant_id,project_id,render_job_id,id);

create table render_graph_execution (
    job_id varchar(128) primary key,
    tenant_id varchar(128) not null,
    project_id varchar(128) not null,
    plan_ref text not null,
    plan_digest varchar(64) not null,
    graph_digest varchar(64) not null,
    attempt_id varchar(128) not null unique,
    generation bigint not null check (generation > 0),
    final_task_id varchar(128) not null,
    task_attempt_id varchar(128),
    task_generation bigint,
    expected_artifact_id varchar(64),
    final_artifact_id varchar(64),
    failure_code varchar(64),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    check ((task_attempt_id is null and task_generation is null and expected_artifact_id is null)
        or (task_attempt_id is not null and task_generation > 0 and expected_artifact_id is not null)),
    foreign key (tenant_id,project_id,job_id) references render_job(tenant_id,project_id,id) on delete restrict,
    foreign key (tenant_id,project_id,job_id,final_artifact_id) references artifact(tenant_id,project_id,render_job_id,id) on delete restrict,
    foreign key (task_attempt_id,final_task_id,task_generation) references wf_execution_attempt(attempt_id,task_id,generation) on delete restrict,
    check (final_artifact_id is null or final_artifact_id = expected_artifact_id)
);
