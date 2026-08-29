package com.example.platform.render.infrastructure;
import org.jooq.impl.DSL;
import java.util.Map;

import com.example.platform.render.app.dto.RenderJobResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Project.PROJECT;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;


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
        dsl.insertInto(RENDER_JOB)
                .columns(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS, RENDER_JOB.CREATED_AT)
                .values(id, projectId, tenantId,
                        timelineSnapshotId, profile, status, createdAt.toLocalDateTime())
                .execute();
    }

    /**
     * Find a render job by ID. Returns empty if not found.
     */
    public Optional<RenderJobResponse> findById(String jobId) {
        Record record = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    /**
     * Find a render job by ID and project ID and tenant ID.
     * Returns empty if not found or tenant/project mismatch.
     */
    public Optional<RenderJobResponse> findByIdAndProjectAndTenant(String jobId, String projectId, String tenantId) {
        Record record = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .and(RENDER_JOB.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    /**
     * List all render jobs for a tenant.
     */
    public List<RenderJobResponse> listByTenant(String tenantId) {
        return dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.TENANT_ID.eq(tenantId))
                .fetch(r -> mapToResponse(r));
    }

    /**
     * List all render jobs for a project within a tenant.
     */
    public List<RenderJobResponse> listByProjectAndTenant(String projectId, String tenantId) {
        return dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.TENANT_ID.eq(tenantId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .fetch(r -> mapToResponse(r));
    }

    /**
     * List all render jobs (no tenant filter — use with caution, preferably only for admin).
     */
    public List<RenderJobResponse> listAll() {
        return dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .fetch(r -> mapToResponse(r));
    }

    /**
     * Update the status of a render job.
     */
    public List<Record> findQueuedJobs(int limit) {
        return dsl.select()
                .from(RENDER_JOB)
                .where(RENDER_JOB.STATUS.eq("QUEUED"))
                .orderBy(RENDER_JOB.CREATED_AT.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * Atomic CAS claim for /start: QUEUED → SELECTING_PROVIDER.
     * Returns 1 if this request won the claim, 0 if another request already claimed it.
     */
    public int claimForSelection(String jobId) {
        return dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, "SELECTING_PROVIDER")
                .set(RENDER_JOB.UPDATED_AT, java.time.Instant.now())
                .where(RENDER_JOB.ID.eq(jobId).and(RENDER_JOB.STATUS.eq("QUEUED")))
                .execute();
    }

    public int claimJob(String jobId) {
        return claimJob(jobId, "provider-worker");
    }

    public int claimJob(String jobId, String workerId) {
        return dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, "EXECUTING")
                .set(RENDER_JOB.UPDATED_AT, java.time.Instant.now())
                .where(RENDER_JOB.ID.eq(jobId).and(RENDER_JOB.STATUS.eq("QUEUED")))
                .execute();
    }

    public List<Record> findStaleExecutingJobs(java.time.Instant cutoff, int limit) {
        return dsl.select()
                .from(RENDER_JOB)
                .where(RENDER_JOB.STATUS.eq("EXECUTING")
                        .and(RENDER_JOB.UPDATED_AT.lessThan(cutoff)))
                .orderBy(RENDER_JOB.UPDATED_AT.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * Durable failure: SELECTING_PROVIDER or EXECUTING → FAILED.
     * Atomic CAS — only succeeds if job is in one of these active states.
     */
    public int markActiveJobFailed(String jobId, String reason) {
        return dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, "FAILED")
                .set(RENDER_JOB.ERROR_MESSAGE, reason)
                .set(RENDER_JOB.UPDATED_AT, java.time.Instant.now())
                .where(RENDER_JOB.ID.eq(jobId).and(
                        RENDER_JOB.STATUS.in("SELECTING_PROVIDER", "PROVIDER_SELECTED", "EXECUTING", "COMPLETING")))
                .execute();
    }

    public int markExecutingJobFailed(String jobId, String reason) {
        return dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, "FAILED")
                .set(RENDER_JOB.ERROR_MESSAGE, reason)
                .set(RENDER_JOB.UPDATED_AT, java.time.Instant.now())
                .where(RENDER_JOB.ID.eq(jobId).and(RENDER_JOB.STATUS.eq("EXECUTING")))
                .execute();
    }

    
    public void updateStatus(String jobId, String newStatus) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, newStatus)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update status and clear error message (e.g., for retry).
     */
    public void updateStatusAndClearError(String jobId, String newStatus) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, newStatus)
                .set(RENDER_JOB.ERROR_MESSAGE, (String) null)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update status and set error message (e.g., for failure).
     */
    public void updateStatusWithError(String jobId, String newStatus, String errorMessage) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.STATUS, newStatus)
                .set(RENDER_JOB.ERROR_MESSAGE, errorMessage)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update the artifact URI for a completed render job.
     */
    public void updateArtifactUri(String jobId, String artifactUri) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.ARTIFACT_URI, artifactUri)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update pipeline plan JSON.
     */
    public void updatePipelinePlan(String jobId, String pipelinePlanJson) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.PIPELINE_PLAN_JSON, pipelinePlanJson)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update pipeline execution JSON.
     */
    public void updatePipelineExecution(String jobId, String pipelineExecutionJson) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.PIPELINE_EXECUTION_JSON, pipelineExecutionJson)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update AI script.
     */
    public void updateAiScript(String jobId, String aiScript) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.AI_SCRIPT, aiScript)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Check if a render job exists and belongs to the given tenant.
     */
    public boolean existsByIdAndTenant(String jobId, String tenantId) {
        return dsl.selectOne()
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .and(RENDER_JOB.TENANT_ID.eq(tenantId))
                .fetchOne() != null;
    }

    /**
     * Get the tenant_id for a render job. Returns empty if not found.
     */
    public Optional<String> findTenantIdById(String jobId) {
        Record record = dsl.select(RENDER_JOB.TENANT_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(RENDER_JOB.TENANT_ID));
    }

    /**
     * Look up the tenant_id for a project. Returns empty if project not found.
     * Note: this queries the project table, not render_job. It is here for convenience
     * since RenderJobService needs it for tenant resolution during job creation.
     */
    public Optional<String> findProjectTenantId(String projectId) {
        Record record = dsl.select(PROJECT.TENANT_ID)
                .from(PROJECT)
                .where(PROJECT.ID.eq(projectId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(PROJECT.TENANT_ID));
    }

    /**
     * Create a quota-rejected render job with error message.
     */
    public void createRejected(String id, String projectId, String tenantId,
            String snapshotId, String profile, String errorMessage, OffsetDateTime createdAt) {
        dsl.insertInto(RENDER_JOB)
                .columns(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID,
                        RENDER_JOB.PROFILE, RENDER_JOB.STATUS, RENDER_JOB.CREATED_AT, RENDER_JOB.ERROR_MESSAGE)
                .values(id, projectId, tenantId, snapshotId, profile,
                        "REJECTED", createdAt.toLocalDateTime(), errorMessage)
                .execute();
    }

    /**
     * Get the ai_script for a render job. Returns empty if not found or null.
     */
    public Optional<String> findAiScriptById(String jobId) {
        Record record = dsl.select(RENDER_JOB.AI_SCRIPT)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(RENDER_JOB.AI_SCRIPT));
    }

    /**
     * Timeline data for a render job (tenant_id, ai_script, timeline_snapshot_id).
     * Used by BaseJobTimelineLoader to avoid inline jOOQ.
     */
    public record TimelineData(String projectId, String tenantId, String aiScript, String timelineSnapshotId) {}

    public Optional<TimelineData> findTimelineDataById(String jobId) {
        Record record = dsl.select(
                        RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.AI_SCRIPT,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(new TimelineData(
                record.get(RENDER_JOB.PROJECT_ID),
                record.get(RENDER_JOB.TENANT_ID),
                record.get(RENDER_JOB.AI_SCRIPT),
                record.get(RENDER_JOB.TIMELINE_SNAPSHOT_ID)));
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
        Record job = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.PROFILE, RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.BASE_JOB_ID,
                        RENDER_JOB.STATUS, RENDER_JOB.AI_SCRIPT, RENDER_JOB.ARTIFACT_URI, RENDER_JOB.ERROR_MESSAGE)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
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
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.PROFILE, profile)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update the trace_id for a render job (provider runtime observability).
     */
    public void updateTraceId(String jobId, String traceId) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.TRACE_ID, traceId)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update the error_message for a render job.
     */
    public void updateErrorMessage(String jobId, String errorMessage) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.ERROR_MESSAGE, errorMessage)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Update the selected_provider for a render job (Provider selection persistence).
     */
    public void updateSelectedProvider(String jobId, String providerName) {
        dsl.update(RENDER_JOB)
                .set(RENDER_JOB.SELECTED_PROVIDER, providerName)
                .where(RENDER_JOB.ID.eq(jobId))
                .execute();
    }

    /**
     * Check if a job is in a cancelled state.
     */
    public boolean isCancelled(String jobId) {
        Record record = dsl.select(RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        return record != null && "CANCELLED".equals(record.get(RENDER_JOB.STATUS, String.class));
    }

    /**
     * Find the ID of the next QUEUED job (FIFO by creation time).
     * Returns empty if no queued jobs exist.
     */
    public Optional<String> findNextQueuedJobId() {
        Record record = dsl.select(RENDER_JOB.ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.STATUS.eq("QUEUED"))
                .orderBy(RENDER_JOB.CREATED_AT.asc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(record).map(r -> r.get(RENDER_JOB.ID, String.class));
    }

    private RenderJobResponse mapToResponse(Record record) {
        return new RenderJobResponse(
                record.get(RENDER_JOB.ID),
                record.get(RENDER_JOB.PROJECT_ID),
                record.get(RENDER_JOB.TIMELINE_SNAPSHOT_ID),
                record.get(RENDER_JOB.PROFILE),
                record.get(RENDER_JOB.STATUS)
        );
    }

    public List<Record> findRetryEligibleFailedJobs(java.time.Instant now, int limit) {
        return dsl.select()
                .from(RENDER_JOB)
                .where(RENDER_JOB.STATUS.eq("FAILED")
                        .and(RENDER_JOB.ERROR_MESSAGE.like("%RETRYABLE%")))
                .orderBy(RENDER_JOB.CREATED_AT.asc())
                .limit(limit)
                .fetch();
    }

    
    /**
     * Create a new RenderJob as a retry of a failed job.
     * The old job remains unchanged (FAILED). The new job references it via base_job_id.
     *
     * @param newId              new RenderJob ID
     * @param failedJobId        the failed RenderJob ID (becomes base_job_id)
     * @param projectId          project ID (copied from old job)
     * @param tenantId           tenant ID (copied from old job)
     * @param timelineSnapshotId timeline snapshot ID (copied from old job)
     * @param profile            profile (copied from old job)
     */
    public void createRetryJob(String newId, String failedJobId, String projectId,
            String tenantId, String timelineSnapshotId, String profile) {
        dsl.insertInto(RENDER_JOB)
                .columns(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS, RENDER_JOB.BASE_JOB_ID, RENDER_JOB.CREATED_AT)
                .values(newId, projectId, tenantId,
                        timelineSnapshotId, profile,
                        "QUEUED", failedJobId, java.time.LocalDateTime.now())
                .execute();
    }


    // === Metrics Queries ===

    public Map<String, Integer> countByStatus(String projectId) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        var results = dsl.select(RENDER_JOB.STATUS, DSL.count().as("cnt"))
                .from(RENDER_JOB)
                .where(RENDER_JOB.PROJECT_ID.eq(projectId))
                .groupBy(RENDER_JOB.STATUS)
                .fetch();
        for (var row : results) {
            counts.put(row.get("status", String.class), row.get("cnt", Integer.class));
        }
        return counts;
    }

    public int countStaleExecuting(String projectId, java.time.Instant cutoff) {
        return dsl.fetchCount(RENDER_JOB,
                RENDER_JOB.PROJECT_ID.eq(projectId)
                        .and(RENDER_JOB.STATUS.eq("EXECUTING"))
                        .and(RENDER_JOB.UPDATED_AT.lessThan(cutoff)));
    }

    public int countRetryEligibleFailed(String projectId) {
        return dsl.fetchCount(RENDER_JOB,
                RENDER_JOB.PROJECT_ID.eq(projectId)
                        .and(RENDER_JOB.STATUS.eq("FAILED"))
                        .and(RENDER_JOB.ERROR_MESSAGE.like("%RETRYABLE%")));
    }

    public int countRetryExhausted(String projectId) {
        return dsl.fetchCount(RENDER_JOB,
                RENDER_JOB.PROJECT_ID.eq(projectId)
                        .and(RENDER_JOB.STATUS.eq("FAILED"))
                        .and(RENDER_JOB.ERROR_MESSAGE.like("%RETRY_EXHAUSTED%")));
    }

    public java.time.Instant oldestQueuedCreatedAt(String projectId) {
        return dsl.select(DSL.min(RENDER_JOB.CREATED_AT))
                .from(RENDER_JOB)
                .where(RENDER_JOB.PROJECT_ID.eq(projectId).and(RENDER_JOB.STATUS.eq("QUEUED")))
                .fetchOneInto(java.sql.Timestamp.class)
                .toInstant();
    }

    public java.time.Instant oldestExecutingUpdatedAt(String projectId) {
        return dsl.select(DSL.min(RENDER_JOB.UPDATED_AT))
                .from(RENDER_JOB)
                .where(RENDER_JOB.PROJECT_ID.eq(projectId).and(RENDER_JOB.STATUS.eq("EXECUTING")))
                .fetchOneInto(java.sql.Timestamp.class)
                .toInstant();
    }

}