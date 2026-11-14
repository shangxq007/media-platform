package com.example.platform.config.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Durable RenderJob submission idempotency reservation. The DB primary key is the concurrency authority. */
@Service
public class RenderSubmissionIdempotencyService {
    private final JdbcTemplate jdbc;
    public RenderSubmissionIdempotencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String requestHash(String revisionId, long startMillis, long endMillis,
            int rateNumerator, int rateDenominator, String profile, String outputFormat) {
        var rate = com.example.platform.shared.time.FrameRate.of(rateNumerator, rateDenominator);
        String canonical = String.join("\n", revisionId, Long.toString(startMillis), Long.toString(endMillis),
                rate.numerator() + "/" + rate.denominator(), profile, outputFormat);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public Reservation reserve(String tenant, String project, String key, String hash) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Submission transaction required");
        int inserted = jdbc.update("insert into render_submission_idempotency(tenant_id,project_id,idempotency_key,request_hash) "
                + "values (?,?,?,?) on conflict (tenant_id,project_id,idempotency_key) do nothing",
                tenant, project, key, hash);
        if (inserted == 1) {
            return new Reservation(true, null, null, null, null, false);
        }
        var row = jdbc.queryForMap("select request_hash,render_job_id,request_id,execution_plan_id,workflow_id,workflow_started "
                + "from render_submission_idempotency where tenant_id=? and project_id=? and idempotency_key=?", tenant, project, key);
        if (!hash.equals(row.get("request_hash")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
        if (row.get("render_job_id") == null) throw new IllegalStateException("Incomplete committed reservation");
        return new Reservation(false, (String) row.get("render_job_id"), (String) row.get("request_id"),
                (String) row.get("execution_plan_id"), (String) row.get("workflow_id"), Boolean.TRUE.equals(row.get("workflow_started")));
    }

    public void complete(String tenant, String project, String key, String jobId, String requestId,
            String executionPlanId, String workflowId, boolean workflowStarted) {
        int changed = jdbc.update("update render_submission_idempotency set render_job_id=?,request_id=?,execution_plan_id=?,"
                + "workflow_id=?,workflow_started=?,lifecycle_status='PLANNED',updated_at=now() "
                + "where tenant_id=? and project_id=? and idempotency_key=? and render_job_id is null",
                jobId, requestId, executionPlanId, workflowId, workflowStarted, tenant, project, key);
        if (changed != 1) throw new IllegalStateException("Reservation ownership lost");
    }

    public record Reservation(boolean created, String renderJobId, String requestId,
            String executionPlanId, String workflowId, boolean workflowStarted) {}
}
