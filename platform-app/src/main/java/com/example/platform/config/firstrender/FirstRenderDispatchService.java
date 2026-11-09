package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.BoundGraphReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;

/** Reconciles the durable DISPATCH intent. Render remains the only lifecycle/fencing owner. */
@Service
public class FirstRenderDispatchService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FirstRenderDispatchService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FirstRenderWorkflowStarter starter;
    public FirstRenderDispatchService(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
            FirstRenderWorkflowStarter starter) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager); this.starter = starter;
    }
    public boolean tryDispatch(String tenant, String job) {
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                // Same Render row lock as cancellation, begin and terminal acceptance. No competing rebind.
                var rows = jdbc.queryForList("select j.status,j.graph_preparation_stage,j.graph_preparation_deadline,b.plan_ref,b.plan_digest,b.expected_etg_digest "
                        + "from render_job j join render_binding_inputs b on b.render_job_id=j.id and b.tenant_id=j.tenant_id "
                        + "where j.id=? and j.tenant_id=? for update of j skip locked", job, tenant);
                if (rows.isEmpty()) return false;
                Map<String,Object> row = rows.getFirst();
                String state = (String) row.get("status");
                if (!"QUEUED".equals(state)) {
                    // A real Render execution is durable proof that the workflow passed begin, including a lost start response.
                    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from render_graph_execution where tenant_id=? and job_id=?)", Boolean.class, tenant, job));
                }
                if (!"DISPATCH".equals(row.get("graph_preparation_stage"))) return false;
                var deadline = (java.sql.Timestamp) row.get("graph_preparation_deadline");
                if (deadline == null || !deadline.toInstant().isAfter(java.time.Instant.now())) return false;
                var reference = new BoundGraphReference(tenant, job, (String) row.get("plan_ref"), (String) row.get("plan_digest"), (String) row.get("expected_etg_digest"));
                String workflow = com.example.platform.workflow.temporal.RenderWorkflowIdentity.forJob(tenant, job);
                boolean started = starter.start(reference, tenant, workflow);
                if (started) jdbc.update("update first_render_idempotency set workflow_started=true,updated_at=now() where tenant_id=? and render_job_id=?", tenant, job);
                return started;
            }));
        } catch (RuntimeException uncertain) {
            // Includes accepted-but-response-lost. Keep immutable intent; stable non-reusable workflow ID resolves ambiguity.
            log.warn("First Render dispatch pending: tenant={} job={} cause={}", tenant, job, uncertain.getClass().getSimpleName());
            return false;
        }
    }
    @Scheduled(fixedDelayString = "${render.first-render.dispatch-delay-ms:5000}")
    public void reconcile() {
        var rows = jdbc.queryForList("select j.tenant_id,j.id from render_job j join render_binding_inputs b "
                + "on b.tenant_id=j.tenant_id and b.render_job_id=j.id where j.status='QUEUED' "
                + "and j.graph_preparation_stage='DISPATCH' and j.graph_preparation_deadline>now() order by j.created_at limit 100");
        for (var row : rows) tryDispatch((String) row.get("tenant_id"), (String) row.get("id"));
    }
}
