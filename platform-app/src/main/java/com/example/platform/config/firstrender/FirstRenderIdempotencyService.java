package com.example.platform.config.firstrender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Durable First Render idempotency reservation. The DB primary key is the concurrency authority. */
@Service
public class FirstRenderIdempotencyService {
    private final JdbcTemplate jdbc;
    public FirstRenderIdempotencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String requestHash(String revisionId, long startMillis, long endMillis,
            int rateNumerator, int rateDenominator, String profile, String outputFormat) {
        String canonical = String.join("\n", revisionId, Long.toString(startMillis), Long.toString(endMillis),
                rateNumerator + "/" + rateDenominator, profile, outputFormat);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public Reservation reserve(String tenant, String project, String key, String hash) {
        int inserted = jdbc.update("insert into first_render_idempotency(tenant_id,project_id,idempotency_key,request_hash) "
                + "values (?,?,?,?) on conflict (tenant_id,project_id,idempotency_key) do nothing",
                tenant, project, key, hash);
        if (inserted == 1) {
            return new Reservation(true, null, null, null, null, false);
        }
        for (int i = 0; i < 100; i++) {
            var rows = jdbc.queryForList("select request_hash,render_job_id,request_id,execution_plan_id,workflow_id,workflow_started "
                    + "from first_render_idempotency where tenant_id=? and project_id=? and idempotency_key=?",
                    tenant, project, key);
            if (rows.isEmpty()) throw new IllegalStateException("idempotency reservation disappeared");
            var row = rows.getFirst();
            if (!hash.equals(row.get("request_hash"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
            }
            Object job = row.get("render_job_id");
            if (job != null) {
                return new Reservation(false, job.toString(), (String) row.get("request_id"),
                        (String) row.get("execution_plan_id"), (String) row.get("workflow_id"),
                        Boolean.TRUE.equals(row.get("workflow_started")));
            }
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS");
    }

    public void complete(String tenant, String project, String key, String jobId, String requestId,
            String executionPlanId, String workflowId, boolean workflowStarted) {
        jdbc.update("update first_render_idempotency set render_job_id=?,request_id=?,execution_plan_id=?,"
                + "workflow_id=?,workflow_started=?,lifecycle_status='PLANNED',updated_at=now() "
                + "where tenant_id=? and project_id=? and idempotency_key=? and render_job_id is null",
                jobId, requestId, executionPlanId, workflowId, workflowStarted, tenant, project, key);
    }

    public void release(String tenant, String project, String key) {
        jdbc.update("delete from first_render_idempotency where tenant_id=? and project_id=? and idempotency_key=? and render_job_id is null",
                tenant, project, key);
    }

    public record Reservation(boolean created, String renderJobId, String requestId,
            String executionPlanId, String workflowId, boolean workflowStarted) {}
}
