package com.example.platform.render.infrastructure;

import com.example.platform.render.api.GraphRenderLifecycle.*;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** SQL for the Render-owned graph fence. Callers hold the canonical job row lock. */
@Repository
public class GraphRenderExecutionRepository {
    private final DSLContext db;
    public GraphRenderExecutionRepository(DSLContext db) { this.db = db; }
    public Optional<Record> find(String tenant, String project, String job) {
        return db.fetchOptional("select e.*,j.status from render_graph_execution e join render_job j on j.id=e.job_id and j.tenant_id=e.tenant_id and j.project_id=e.project_id where e.tenant_id=? and e.project_id=? and e.job_id=?", tenant, project, job);
    }
    public void insert(Execution e) {
        db.execute("insert into render_graph_execution(job_id,tenant_id,project_id,plan_ref,plan_digest,graph_digest,attempt_id,generation,final_task_id) values(?,?,?,?,?,?,?,?,?)",
                e.jobId(),e.tenantId(),e.projectId(),e.planRef(),e.planDigest(),e.graphDigest(),e.attemptId(),e.generation(),e.finalTaskId());
    }
    public void bind(Execution e, Output o) {
        db.execute("update render_graph_execution set task_attempt_id=?,task_generation=?,expected_artifact_id=?,updated_at=current_timestamp where job_id=?",
                o.taskAttemptId(),o.taskGeneration(),o.artifactId(),e.jobId());
    }
    public void accept(Execution e, String artifact) {
        db.execute("update render_graph_execution set final_artifact_id=?,updated_at=current_timestamp where job_id=?",artifact,e.jobId());
    }
    public void failure(String job, String code) {
        db.execute("update render_graph_execution set failure_code=?,updated_at=current_timestamp where job_id=?",code,job);
    }
    public static Execution execution(Record r) {
        return new Execution(r.get("tenant_id",String.class),r.get("project_id",String.class),r.get("job_id",String.class),
                r.get("plan_ref",String.class),r.get("plan_digest",String.class),r.get("graph_digest",String.class),
                r.get("attempt_id",String.class),r.get("generation",Long.class),r.get("final_task_id",String.class));
    }
}
