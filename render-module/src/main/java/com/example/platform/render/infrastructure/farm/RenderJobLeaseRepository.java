package com.example.platform.render.infrastructure.farm;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code render_job_lease} table.
 *
 * <p>Provides atomic claim, renew, release, fail, and expire operations
 * for the job lease queue.
 */
@Repository
public class RenderJobLeaseRepository {

    private final DSLContext dsl;

    public RenderJobLeaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Create a new lease record.
     */
    public void create(RenderJobLeaseRecord lease) {
        dsl.insertInto(table("render_job_lease"))
                .columns(field("id"), field("lease_id"), field("job_id"), field("tenant_id"),
                        field("worker_id"), field("provider_id"), field("status"),
                        field("lease_version"), field("claimed_at"), field("lease_until"),
                        field("attempt"), field("max_attempts"),
                        field("heartbeat_token_hash"), field("created_by_scheduler"),
                        field("created_at"), field("updated_at"))
                .values(lease.id(), lease.leaseId(), lease.jobId(), lease.tenantId(),
                        lease.workerId(), lease.providerId(), lease.status().name(),
                        lease.leaseVersion(), toTs(lease.claimedAt()), toTs(lease.leaseUntil()),
                        lease.attempt(), lease.maxAttempts(),
                        lease.heartbeatTokenHash(), lease.createdByScheduler(),
                        toTs(lease.createdAt()), toTs(lease.updatedAt()))
                .execute();
    }

    /**
     * Find an active lease for a given job. Returns empty if no active lease exists.
     */
    public Optional<RenderJobLeaseRecord> findActiveLeaseByJobId(String jobId) {
        Record r = dsl.select()
                .from(table("render_job_lease"))
                .where(field("job_id").eq(jobId))
                .and(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapRecord);
    }

    /**
     * Find a lease by lease_id.
     */
    public Optional<RenderJobLeaseRecord> findByLeaseId(String leaseId) {
        Record r = dsl.select()
                .from(table("render_job_lease"))
                .where(field("lease_id").eq(leaseId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapRecord);
    }

    /**
     * Find leases by worker_id.
     */
    public List<RenderJobLeaseRecord> findByWorkerId(String workerId) {
        return dsl.select()
                .from(table("render_job_lease"))
                .where(field("worker_id").eq(workerId))
                .fetch(this::mapRecord);
    }

    /**
     * Find all active leases (CLAIMED, RUNNING, RENEWED).
     */
    public List<RenderJobLeaseRecord> findActiveLeases() {
        return dsl.select()
                .from(table("render_job_lease"))
                .where(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .fetch(this::mapRecord);
    }

    /**
     * Renew a lease. Only succeeds if the lease is active and owned by the given worker.
     * Uses optimistic locking via lease_version.
     *
     * @return true if the lease was renewed
     */
    public boolean renew(String leaseId, String workerId, long expectedVersion, Instant newLeaseUntil, Instant now) {
        int rows = dsl.update(table("render_job_lease"))
                .set(field("status"), RenderJobLeaseStatus.RENEWED.name())
                .set(field("lease_until"), toTs(newLeaseUntil))
                .set(field("renewed_at"), toTs(now))
                .set(field("lease_version"), expectedVersion + 1)
                .set(field("updated_at"), toTs(now))
                .where(field("lease_id").eq(leaseId))
                .and(field("worker_id").eq(workerId))
                .and(field("lease_version").eq(expectedVersion))
                .and(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .execute();
        return rows > 0;
    }

    /**
     * Release a lease (job completed successfully).
     *
     * @return true if the lease was released
     */
    public boolean release(String leaseId, String workerId, long expectedVersion, Instant now) {
        int rows = dsl.update(table("render_job_lease"))
                .set(field("status"), RenderJobLeaseStatus.RELEASED.name())
                .set(field("released_at"), toTs(now))
                .set(field("lease_version"), expectedVersion + 1)
                .set(field("updated_at"), toTs(now))
                .where(field("lease_id").eq(leaseId))
                .and(field("worker_id").eq(workerId))
                .and(field("lease_version").eq(expectedVersion))
                .and(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .execute();
        return rows > 0;
    }

    /**
     * Fail a lease (job execution failed).
     *
     * @return true if the lease was marked failed
     */
    public boolean fail(String leaseId, String workerId, long expectedVersion,
            String failureReason, String failureErrorCode, Instant now) {
        int rows = dsl.update(table("render_job_lease"))
                .set(field("status"), RenderJobLeaseStatus.FAILED.name())
                .set(field("failure_reason"), failureReason)
                .set(field("failure_error_code"), failureErrorCode)
                .set(field("released_at"), toTs(now))
                .set(field("lease_version"), expectedVersion + 1)
                .set(field("updated_at"), toTs(now))
                .where(field("lease_id").eq(leaseId))
                .and(field("worker_id").eq(workerId))
                .and(field("lease_version").eq(expectedVersion))
                .and(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .execute();
        return rows > 0;
    }

    /**
     * Expire stale leases whose lease_until has passed.
     * Returns the list of expired lease records.
     */
    public List<RenderJobLeaseRecord> expireStaleLeases(Instant now) {
        // First, find leases to expire
        List<RenderJobLeaseRecord> stale = dsl.select()
                .from(table("render_job_lease"))
                .where(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .and(field("lease_until").lt(toTs(now)))
                .fetch(this::mapRecord);

        if (stale.isEmpty()) {
            return stale;
        }

        // Mark them expired
        dsl.update(table("render_job_lease"))
                .set(field("status"), RenderJobLeaseStatus.EXPIRED.name())
                .set(field("updated_at"), toTs(now))
                .where(field("status").in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .and(field("lease_until").lt(toTs(now)))
                .execute();

        return stale;
    }

    /**
     * Get the current attempt count for a job (from the most recent lease).
     */
    public int getMaxAttemptForJob(String jobId) {
        Record r = dsl.select(field("attempt"))
                .from(table("render_job_lease"))
                .where(field("job_id").eq(jobId))
                .orderBy(field("attempt").desc())
                .limit(1)
                .fetchOne();
        return r != null ? r.get(field("attempt"), Integer.class) : 0;
    }

    private RenderJobLeaseRecord mapRecord(Record r) {
        return new RenderJobLeaseRecord(
                r.get(field("id"), String.class),
                r.get(field("lease_id"), String.class),
                r.get(field("job_id"), String.class),
                r.get(field("tenant_id"), String.class),
                r.get(field("worker_id"), String.class),
                r.get(field("provider_id"), String.class),
                RenderJobLeaseStatus.valueOf(r.get(field("status"), String.class)),
                r.get(field("lease_version"), Long.class),
                toInstant(r.get(field("claimed_at"))),
                toInstant(r.get(field("lease_until"))),
                toInstant(r.get(field("renewed_at"))),
                toInstant(r.get(field("released_at"))),
                r.get(field("attempt"), Integer.class),
                r.get(field("max_attempts"), Integer.class),
                r.get(field("heartbeat_token_hash"), String.class),
                r.get(field("failure_reason"), String.class),
                r.get(field("failure_error_code"), String.class),
                r.get(field("created_by_scheduler"), String.class),
                toInstant(r.get(field("created_at"))),
                toInstant(r.get(field("updated_at")))
        );
    }

    private static OffsetDateTime toTs(Instant instant) {
        return instant != null ? OffsetDateTime.from(instant.atOffset(java.time.ZoneOffset.UTC)) : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }
}
