package com.example.platform.workflow.run;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/** Workflow-owned facts and projections; Temporal history owns execution mechanics. */
@Repository
public class WorkflowRunStore {
    private final JdbcTemplate jdbc;

    public WorkflowRunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Run(
            String id,
            String tenantId,
            String workspaceId,
            String projectId,
            String requestKey,
            String requestDigest,
            String actorJson,
            String definitionId,
            int definitionVersion,
            String workflowPlanDigest,
            String planJson,
            String inputsJson,
            String bindingsJson,
            String status,
            boolean cancelRequested,
            String failureCode) {}

    private static final org.springframework.jdbc.core.RowMapper<Run> ROW =
            (r, i) ->
                    new Run(
                            r.getString("id"),
                            r.getString("tenant_id"),
                            r.getString("workspace_id"),
                            r.getString("project_id"),
                            r.getString("request_key"),
                            r.getString("request_digest"),
                            r.getString("actor_json"),
                            r.getString("definition_id"),
                            r.getInt("definition_version"),
                            r.getString("workflow_plan_digest"),
                            r.getString("plan_json"),
                            r.getString("inputs_json"),
                            r.getString("bindings_json"),
                            r.getString("status"),
                            r.getBoolean("cancel_requested"),
                            r.getString("failure_code"));

    public Optional<Run> find(String tenant, String id) {
        return jdbc
                .query("select * from workflow_run where tenant_id=? and id=?", ROW, tenant, id)
                .stream()
                .findFirst();
    }

    public Run require(String id) {
        return jdbc.query("select * from workflow_run where id=?", ROW, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
    }

    public Run lock(String id) {
        return jdbc.query("select * from workflow_run where id=? for update", ROW, id).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Run not found"));
    }

    public Optional<Run> request(String tenant, String key) {
        jdbc.queryForObject(
                "select pg_advisory_xact_lock(hashtextextended(?,0))",
                Object.class,
                "workflow:" + tenant + ":" + key);
        return jdbc
                .query(
                        "select * from workflow_run where tenant_id=? and request_key=?",
                        ROW,
                        tenant,
                        key)
                .stream()
                .findFirst();
    }

    public void insert(Run run) {
        jdbc.update(
                "insert into"
                    + " workflow_run(id,tenant_id,workspace_id,project_id,request_key,request_digest,actor_json,definition_id,definition_version,workflow_plan_digest,plan_json,inputs_json,bindings_json,status)"
                    + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                run.id(),
                run.tenantId(),
                run.workspaceId(),
                run.projectId(),
                run.requestKey(),
                run.requestDigest(),
                run.actorJson(),
                run.definitionId(),
                run.definitionVersion(),
                run.workflowPlanDigest(),
                run.planJson(),
                run.inputsJson(),
                run.bindingsJson(),
                run.status());
    }

    public List<Map<String, Object>> receipt(String id, String step) {
        return jdbc.queryForList(
                "select request_digest,result_json from workflow_operation_receipt where run_id=?"
                        + " and step_id=?",
                id,
                step);
    }

    public void receipt(String id, String step, String digest, String result) {
        jdbc.update(
                "insert into workflow_operation_receipt(run_id,step_id,request_digest,result_json)"
                        + " values (?,?,?,?)",
                id,
                step,
                digest,
                result);
    }

    public void running(String id) {
        jdbc.update(
                "update workflow_run set status='RUNNING' where id=? and status='ACCEPTED'", id);
    }

    public void waiting(String id, String step, String kind, long deadline) {
        jdbc.update(
                "insert into workflow_run_step(run_id,step_id,status,wait_kind,deadline_at) values"
                        + " (?,?,'WAITING',?,?) on conflict (run_id,step_id) do nothing",
                id,
                step,
                kind,
                new java.sql.Timestamp(deadline));
    }

    public void completed(String id, String step, String result) {
        jdbc.update(
                "insert into workflow_run_step(run_id,step_id,status,result_json) values"
                        + " (?,?,'COMPLETED',?) on conflict (run_id,step_id) do update set"
                        + " status='COMPLETED',result_json=excluded.result_json",
                id,
                step,
                result);
    }

    public void terminal(String id, String status, String failure) {
        jdbc.update(
                "update workflow_run set status=?,failure_code=?,completed_at=now() where id=?",
                status,
                failure,
                id);
    }

    public void requestCancel(String id, String actor) {
        jdbc.update(
                "update workflow_run set cancel_requested=true,cancelled_by_json=? where id=?",
                actor,
                id);
    }

    public List<Map<String, Object>> waitProjection(String id, String step) {
        return jdbc.queryForList(
                "select * from workflow_run_step where run_id=? and step_id=?", id, step);
    }

    public List<Map<String, Object>> lockWait(String id, String step) {
        return jdbc.queryForList(
                "select * from workflow_run_step where run_id=? and step_id=? for update",
                id,
                step);
    }

    public int release(String id, String step, String release, boolean approved, String actor) {
        return jdbc.update(
                "update workflow_run_step set release_id=?,approved=?,released_by_json=? where"
                        + " run_id=? and step_id=? and deadline_at>now()",
                release,
                approved,
                actor,
                id,
                step);
    }

    public boolean completed(String id, String step) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select exists(select 1 from workflow_run_step where run_id=? and step_id=?"
                                + " and status='COMPLETED')",
                        Boolean.class,
                        id,
                        step));
    }

    public void failedStep(String id, String step, String status, String failure) {
        if (step != null)
            jdbc.update(
                    "insert into workflow_run_step(run_id,step_id,status,failure_code) values"
                            + " (?,?,?,?) on conflict (run_id,step_id) do update set"
                            + " status=excluded.status,failure_code=excluded.failure_code where"
                            + " workflow_run_step.status<>'COMPLETED'",
                    id,
                    step,
                    status,
                    failure);
        jdbc.update(
                "update workflow_run_step set status=?,failure_code=? where run_id=? and"
                        + " status='WAITING'",
                status,
                failure,
                id);
    }

    public static boolean terminal(Run run) {
        return Set.of("SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT").contains(run.status());
    }

    public List<Map<String, Object>> steps(String id) {
        return jdbc.queryForList(
                "select"
                    + " step_id,status,failure_code,wait_kind,deadline_at,release_id,approved,result_json"
                    + " from workflow_run_step where run_id=? order by step_id",
                id);
    }
}
