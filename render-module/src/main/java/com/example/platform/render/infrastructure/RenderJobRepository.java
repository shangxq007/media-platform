package com.example.platform.render.infrastructure;
import org.jooq.impl.DSL;
import java.util.Map;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.app.dto.RenderJobResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code render_job} table.
 *
 * <p>All column/field references for render_job are centralized here.
 * Services should use this repository instead of inline jOOQ DSL.
 */
@Repository
public class RenderJobRepository {

    private final DSLContext dsl;

    public RenderJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Insert a new render job.
     */
    public void create(String id, String projectId, String tenantId,
            String timelineSnapshotId, String profile, String status, OffsetDateTime createdAt) {
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"),
                        field("status"), field("created_at"))
                .values(id, projectId, tenantId,
                        timelineSnapshotId, profile, status, createdAt)
                .execute();
    }

    /**
     * Find a render job by ID. Returns empty if not found.
     */
    public Optional<RenderJobResponse> findById(String jobId) {
        Record record = dsl.select(field("id", String.class), field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    /**
     * Find a render job by ID and project ID and tenant ID.
     * Returns empty if not found or tenant/project mismatch.
     */
    public Optional<RenderJobResponse> findByIdAndProjectAndTenant(String jobId, String projectId, String tenantId) {
        Record record = dsl.select(field("id", String.class), field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .and(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    /**
     * List all render jobs for a tenant.
     */
    public List<RenderJobResponse> listByTenant(String tenantId) {
        return dsl.select(field("id", String.class), field("project_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("tenant_id").eq(tenantId))
                .fetch(r -> mapToResponse(r));
    }

    /**
     * List all render jobs for a project within a tenant.
     */
    public List<RenderJobResponse> listByProjectAndTenant(String projectId, String tenantId) {
        return dsl.select(field("id", String.class), field("project_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .fetch(r -> mapToResponse(r));
    }

    /**
     * List all render jobs (no tenant filter — use with caution, preferably only for admin).
     */
    public List<RenderJobResponse> listAll() {
        return dsl.select(field("id", String.class), field("project_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .fetch(r -> mapToResponse(r));
    }

    /**
     * Update the status of a render job.
     */
    public List<Record> findQueuedJobs(int limit) {
        return dsl.select()
                .from(table("render_job"))
                .where(field("status").eq("QUEUED"))
                .orderBy(field("created_at").asc())
                .limit(limit)
                .fetch();
    }

    /**
     * Atomic CAS claim for /start: QUEUED → SELECTING_PROVIDER.
     * Returns 1 if this request won the claim, 0 if another request already claimed it.
     */
    public int claimForSelection(String jobId) {
        return dsl.update(table("render_job"))
                .set(field("status"), "SELECTING_PROVIDER")
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(field("status").eq("QUEUED")))
                .execute();
    }

    public int claimJob(String jobId) {
        return claimJob(jobId, "ffmpeg-worker");
    }

    public int claimJob(String jobId, String workerId) {
        return dsl.update(table("render_job"))
                .set(field("status"), "EXECUTING")
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(field("status").eq("QUEUED")))
                .execute();
    }

    public List<Record> findStaleExecutingJobs(java.time.Instant cutoff, int limit) {
        return dsl.select()
                .from(table("render_job"))
                .where(field("status").eq("EXECUTING")
                        .and(field("updated_at").lessThan(java.sql.Timestamp.from(cutoff))))
                .orderBy(field("updated_at").asc())
                .limit(limit)
                .fetch();
    }

    /**
     * Durable failure: SELECTING_PROVIDER or EXECUTING → FAILED.
     * Atomic CAS — only succeeds if job is in one of these active states.
     */
    public int markActiveJobFailed(String jobId, String reason) {
        return dsl.update(table("render_job"))
                .set(field("status"), "FAILED")
                .set(field("error_message"), reason)
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(
                        field("status").in("SELECTING_PROVIDER", "EXECUTING")))
                .execute();
    }

    public int markExecutingJobFailed(String jobId, String reason) {
        return dsl.update(table("render_job"))
                .set(field("status"), "FAILED")
                .set(field("error_message"), reason)
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(field("status").eq("EXECUTING")))
                .execute();
    }

    public int requeueExecutingJob(String jobId, String reason) {
        return dsl.update(table("render_job"))
                .set(field("status"), "QUEUED")
                .set(field("error_message"), reason)
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(field("status").eq("EXECUTING")))
                .execute();
    }

    public void updateStatus(String jobId, String newStatus) {
        dsl.update(table("render_job"))
                .set(field("status"), newStatus)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update status and clear error message (e.g., for retry).
     */
    public void updateStatusAndClearError(String jobId, String newStatus) {
        dsl.update(table("render_job"))
                .set(field("status"), newStatus)
                .set(field("error_message"), (String) null)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update status and set error message (e.g., for failure).
     */
    public void updateStatusWithError(String jobId, String newStatus, String errorMessage) {
        dsl.update(table("render_job"))
                .set(field("status"), newStatus)
                .set(field("error_message"), errorMessage)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update the artifact URI for a completed render job.
     */
    public void updateArtifactUri(String jobId, String artifactUri) {
        dsl.update(table("render_job"))
                .set(field("artifact_uri"), artifactUri)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update pipeline plan JSON.
     */
    public void updatePipelinePlan(String jobId, String pipelinePlanJson) {
        dsl.update(table("render_job"))
                .set(field("pipeline_plan_json"), pipelinePlanJson)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update pipeline execution JSON.
     */
    public void updatePipelineExecution(String jobId, String pipelineExecutionJson) {
        dsl.update(table("render_job"))
                .set(field("pipeline_execution_json"), pipelineExecutionJson)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update AI script.
     */
    public void updateAiScript(String jobId, String aiScript) {
        dsl.update(table("render_job"))
                .set(field("ai_script"), aiScript)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Check if a render job exists and belongs to the given tenant.
     */
    public boolean existsByIdAndTenant(String jobId, String tenantId) {
        return dsl.selectOne()
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne() != null;
    }

    /**
     * Get the tenant_id for a render job. Returns empty if not found.
     */
    public Optional<String> findTenantIdById(String jobId) {
        Record record = dsl.select(field("tenant_id", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(field("tenant_id", String.class)));
    }

    /**
     * Look up the tenant_id for a project. Returns empty if project not found.
     * Note: this queries the project table, not render_job. It is here for convenience
     * since RenderJobService needs it for tenant resolution during job creation.
     */
    public Optional<String> findProjectTenantId(String projectId) {
        Record record = dsl.select(field("tenant_id", String.class))
                .from(table("project"))
                .where(field("id").eq(projectId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(field("tenant_id", String.class)));
    }

    /**
     * Create a quota-rejected render job with error message.
     */
    public void createRejected(String id, String projectId, String tenantId,
            String snapshotId, String profile, String errorMessage, OffsetDateTime createdAt) {
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"),
                        field("profile"), field("status"), field("created_at"), field("error_message"))
                .values(id, projectId, tenantId, snapshotId, profile,
                        "REJECTED", createdAt, errorMessage)
                .execute();
    }

    /**
     * Get the ai_script for a render job. Returns empty if not found or null.
     */
    public Optional<String> findAiScriptById(String jobId) {
        Record record = dsl.select(field("ai_script", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(field("ai_script", String.class)));
    }

    /**
     * Timeline data for a render job (tenant_id, ai_script, timeline_snapshot_id).
     * Used by BaseJobTimelineLoader to avoid inline jOOQ.
     */
    public record TimelineData(String tenantId, String aiScript, String timelineSnapshotId) {}

    public Optional<TimelineData> findTimelineDataById(String jobId) {
        Record record = dsl.select(
                        field("tenant_id", String.class),
                        field("ai_script", String.class),
                        field("timeline_snapshot_id", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(new TimelineData(
                record.get(field("tenant_id", String.class)),
                record.get(field("ai_script", String.class)),
                record.get(field("timeline_snapshot_id", String.class))));
    }

    /**
     * Get the tenant_id for a render job, throwing if not found.
     * Used by services that need tenant validation for an existing job.
     */
    public String requireTenantIdByJobId(String jobId) {
        return findTenantIdById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Render job not found: " + jobId));
    }

    /**
     * Load a full job record with all fields needed for execution.
     * Returns the raw Record for flexible access by execution service.
     */
    public Record requireJobRecord(String jobId) {
        Record job = dsl.select(field("id"), field("project_id"), field("tenant_id"),
                        field("profile"), field("timeline_snapshot_id"), field("base_job_id"),
                        field("status"), field("ai_script"), field("artifact_uri"), field("error_message"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return job;
    }

    /**
     * Update the profile for a render job.
     */
    public void updateProfile(String jobId, String profile) {
        dsl.update(table("render_job"))
                .set(field("profile"), profile)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update the trace_id for a render job (provider runtime observability).
     */
    public void updateTraceId(String jobId, String traceId) {
        dsl.update(table("render_job"))
                .set(field("trace_id"), traceId)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update the error_message for a render job.
     */
    public void updateErrorMessage(String jobId, String errorMessage) {
        dsl.update(table("render_job"))
                .set(field("error_message"), errorMessage)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Update the selected_provider for a render job (Provider selection persistence).
     */
    public void updateSelectedProvider(String jobId, String providerName) {
        dsl.update(table("render_job"))
                .set(field("selected_provider"), providerName)
                .where(field("id").eq(jobId))
                .execute();
    }

    /**
     * Check if a job is in a cancelled state.
     */
    public boolean isCancelled(String jobId) {
        Record record = dsl.select(field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        return record != null && "CANCELLED".equals(record.get(field("status"), String.class));
    }

    /**
     * Find the ID of the next QUEUED job (FIFO by creation time).
     * Returns empty if no queued jobs exist.
     */
    public Optional<String> findNextQueuedJobId() {
        Record record = dsl.select(field("id", String.class))
                .from(table("render_job"))
                .where(field("status").eq("QUEUED"))
                .orderBy(field("created_at").asc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(record).map(r -> r.get(field("id"), String.class));
    }

    private RenderJobResponse mapToResponse(Record record) {
        return new RenderJobResponse(
                record.get(field("id", String.class)),
                record.get(field("project_id", String.class)),
                record.get(field("timeline_snapshot_id", String.class)),
                record.get(field("profile", String.class)),
                record.get(field("status", String.class))
        );
    }

    public List<Record> findRetryEligibleFailedJobs(java.time.Instant now, int limit) {
        return dsl.select()
                .from(table("render_job"))
                .where(field("status").eq("FAILED")
                        .and(field("error_message").like("%RETRYABLE%")))
                .orderBy(field("created_at").asc())
                .limit(limit)
                .fetch();
    }

    public int requeueFailedJob(String jobId) {
        return dsl.update(table("render_job"))
                .set(field("status"), "QUEUED")
                .set(field("updated_at"), java.time.OffsetDateTime.now())
                .where(field("id").eq(jobId).and(field("status").eq("FAILED")))
                .execute();
    }


    // === Metrics Queries ===

    public Map<String, Integer> countByStatus(String projectId) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        var results = dsl.select(field("status"), DSL.count().as("cnt"))
                .from(table("render_job"))
                .where(field("project_id").eq(projectId))
                .groupBy(field("status"))
                .fetch();
        for (var row : results) {
            counts.put(row.get("status", String.class), row.get("cnt", Integer.class));
        }
        return counts;
    }

    public int countStaleExecuting(String projectId, java.time.Instant cutoff) {
        return dsl.fetchCount(table("render_job"),
                field("project_id").eq(projectId)
                        .and(field("status").eq("EXECUTING"))
                        .and(field("updated_at").lessThan(java.sql.Timestamp.from(cutoff))));
    }

    public int countRetryEligibleFailed(String projectId) {
        return dsl.fetchCount(table("render_job"),
                field("project_id").eq(projectId)
                        .and(field("status").eq("FAILED"))
                        .and(field("error_message").like("%RETRYABLE%")));
    }

    public int countRetryExhausted(String projectId) {
        return dsl.fetchCount(table("render_job"),
                field("project_id").eq(projectId)
                        .and(field("status").eq("FAILED"))
                        .and(field("error_message").like("%RETRY_EXHAUSTED%")));
    }

    public java.time.Instant oldestQueuedCreatedAt(String projectId) {
        return dsl.select(DSL.min(field("created_at")))
                .from(table("render_job"))
                .where(field("project_id").eq(projectId).and(field("status").eq("QUEUED")))
                .fetchOneInto(java.sql.Timestamp.class)
                .toInstant();
    }

    public java.time.Instant oldestExecutingUpdatedAt(String projectId) {
        return dsl.select(DSL.min(field("updated_at")))
                .from(table("render_job"))
                .where(field("project_id").eq(projectId).and(field("status").eq("EXECUTING")))
                .fetchOneInto(java.sql.Timestamp.class)
                .toInstant();
    }

}