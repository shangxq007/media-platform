-- Forward-only Workflow process authority. Historical workflow_execution rows remain untouched.
CREATE TABLE workflow_run (
    id text PRIMARY KEY,
    tenant_id text NOT NULL,
    workspace_id text NOT NULL,
    project_id text NOT NULL,
    request_key text NOT NULL,
    request_digest text NOT NULL,
    actor_json text NOT NULL,
    definition_id text NOT NULL,
    definition_version integer NOT NULL,
    workflow_plan_digest text NOT NULL,
    plan_json text NOT NULL,
    inputs_json text NOT NULL,
    bindings_json text NOT NULL,
    status text NOT NULL CHECK (status IN ('ACCEPTED','RUNNING','SUCCEEDED','FAILED','CANCELLED','TIMED_OUT')),
    cancelled_by_json text,
    cancel_requested boolean NOT NULL DEFAULT false,
    failure_code text,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    UNIQUE(tenant_id, request_key),
    FOREIGN KEY(project_id) REFERENCES project(id)
);
CREATE TABLE workflow_run_step (
    run_id text NOT NULL REFERENCES workflow_run(id),
    step_id text NOT NULL,
    status text NOT NULL CHECK (status IN ('WAITING','COMPLETED','FAILED','CANCELLED','TIMED_OUT')),
    failure_code text,
    wait_kind text,
    deadline_at timestamptz,
    release_id text,
    released_by_json text,
    approved boolean,
    result_json text,
    PRIMARY KEY(run_id,step_id)
);
CREATE TABLE workflow_operation_receipt (
    run_id text NOT NULL REFERENCES workflow_run(id),
    step_id text NOT NULL,
    request_digest text NOT NULL,
    result_json text NOT NULL,
    PRIMARY KEY(run_id,step_id)
);
