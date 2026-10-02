-- Bound graph inputs for a render job (P2-5a-2 / P2-5a2c).
--
-- One row per (tenant_id, render_job_id): the canonical, encoded planning inputs
-- that a render job was planned against. The bytes are produced by the bounded V1
-- canonical codecs in media-execution-plan-module; plan_digest is the SHA-256 of the
-- canonical encoded plan. This relation is independent of the legacy render_job
-- pipeline columns and never collides with render_job.pipeline_plan_json.

create table render_binding_inputs (
    id bigserial primary key,
    tenant_id varchar(64) not null,
    render_job_id varchar(64) not null,
    plan_ref varchar(256) not null,
    plan_digest varchar(64) not null,
    expected_etg_digest varchar(128) not null,
    plan_json bytea not null,
    candidates_json bytea not null,
    declarations_json bytea not null,
    created_at timestamptz not null default now(),
    constraint uq_render_binding_inputs_job unique (tenant_id, render_job_id)
);
