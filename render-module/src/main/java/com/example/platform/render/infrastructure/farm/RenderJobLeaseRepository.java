package com.example.platform.render.infrastructure.farm;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobLease.RENDER_JOB_LEASE;


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
        dsl.insertInto(RENDER_JOB_LEASE)
                .columns(RENDER_JOB_LEASE.ID, RENDER_JOB_LEASE.LEASE_ID, RENDER_JOB_LEASE.JOB_ID, RENDER_JOB_LEASE.TENANT_ID,
                        RENDER_JOB_LEASE.WORKER_ID, RENDER_JOB_LEASE.PROVIDER_ID, RENDER_JOB_LEASE.STATUS,
                        RENDER_JOB_LEASE.LEASE_VERSION, RENDER_JOB_LEASE.CLAIMED_AT, RENDER_JOB_LEASE.LEASE_UNTIL,
                        RENDER_JOB_LEASE.ATTEMPT, RENDER_JOB_LEASE.MAX_ATTEMPTS,
                        RENDER_JOB_LEASE.HEARTBEAT_TOKEN_HASH, RENDER_JOB_LEASE.CREATED_BY_SCHEDULER,
                        RENDER_JOB_LEASE.CREATED_AT, RENDER_JOB_LEASE.UPDATED_AT)
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
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.JOB_ID.eq(jobId))
                .and(RENDER_JOB_LEASE.STATUS.in(
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
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapRecord);
    }

    /**
     * Find leases by worker_id.
     */
    public List<RenderJobLeaseRecord> findByWorkerId(String workerId) {
        return dsl.select()
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.WORKER_ID.eq(workerId))
                .fetch(this::mapRecord);
    }

    /**
     * Find all active leases (CLAIMED, RUNNING, RENEWED).
     */
    public List<RenderJobLeaseRecord> findActiveLeases() {
        return dsl.select()
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.STATUS.in(
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
        int rows = dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, RenderJobLeaseStatus.RENEWED.name())
                .set(RENDER_JOB_LEASE.LEASE_UNTIL, toTs(newLeaseUntil))
                .set(RENDER_JOB_LEASE.RENEWED_AT, toTs(now))
                .set(RENDER_JOB_LEASE.LEASE_VERSION, expectedVersion + 1)
                .set(RENDER_JOB_LEASE.UPDATED_AT, toTs(now))
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .and(RENDER_JOB_LEASE.WORKER_ID.eq(workerId))
                .and(RENDER_JOB_LEASE.LEASE_VERSION.eq(expectedVersion))
                .and(RENDER_JOB_LEASE.STATUS.in(
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
        int rows = dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, RenderJobLeaseStatus.RELEASED.name())
                .set(RENDER_JOB_LEASE.RELEASED_AT, toTs(now))
                .set(RENDER_JOB_LEASE.LEASE_VERSION, expectedVersion + 1)
                .set(RENDER_JOB_LEASE.UPDATED_AT, toTs(now))
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .and(RENDER_JOB_LEASE.WORKER_ID.eq(workerId))
                .and(RENDER_JOB_LEASE.LEASE_VERSION.eq(expectedVersion))
                .and(RENDER_JOB_LEASE.STATUS.in(
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
        int rows = dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, RenderJobLeaseStatus.FAILED.name())
                .set(RENDER_JOB_LEASE.FAILURE_REASON, failureReason)
                .set(RENDER_JOB_LEASE.FAILURE_ERROR_CODE, failureErrorCode)
                .set(RENDER_JOB_LEASE.RELEASED_AT, toTs(now))
                .set(RENDER_JOB_LEASE.LEASE_VERSION, expectedVersion + 1)
                .set(RENDER_JOB_LEASE.UPDATED_AT, toTs(now))
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .and(RENDER_JOB_LEASE.WORKER_ID.eq(workerId))
                .and(RENDER_JOB_LEASE.LEASE_VERSION.eq(expectedVersion))
                .and(RENDER_JOB_LEASE.STATUS.in(
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
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.STATUS.in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .and(RENDER_JOB_LEASE.LEASE_UNTIL.lt(toTs(now)))
                .fetch(this::mapRecord);

        if (stale.isEmpty()) {
            return stale;
        }

        // Mark them expired
        dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, RenderJobLeaseStatus.EXPIRED.name())
                .set(RENDER_JOB_LEASE.UPDATED_AT, toTs(now))
                .where(RENDER_JOB_LEASE.STATUS.in(
                        RenderJobLeaseStatus.CLAIMED.name(),
                        RenderJobLeaseStatus.RUNNING.name(),
                        RenderJobLeaseStatus.RENEWED.name()))
                .and(RENDER_JOB_LEASE.LEASE_UNTIL.lt(toTs(now)))
                .execute();

        return stale;
    }

    /**
     * Get the current attempt count for a job (from the most recent lease).
     */
    public int getMaxAttemptForJob(String jobId) {
        Record r = dsl.select(RENDER_JOB_LEASE.ATTEMPT)
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.JOB_ID.eq(jobId))
                .orderBy(RENDER_JOB_LEASE.ATTEMPT.desc())
                .limit(1)
                .fetchOne();
        return r != null ? r.get(RENDER_JOB_LEASE.ATTEMPT, Integer.class) : 0;
    }

    private RenderJobLeaseRecord mapRecord(Record r) {
        return new RenderJobLeaseRecord(
                r.get(RENDER_JOB_LEASE.ID, String.class),
                r.get(RENDER_JOB_LEASE.LEASE_ID, String.class),
                r.get(RENDER_JOB_LEASE.JOB_ID, String.class),
                r.get(RENDER_JOB_LEASE.TENANT_ID, String.class),
                r.get(RENDER_JOB_LEASE.WORKER_ID, String.class),
                r.get(RENDER_JOB_LEASE.PROVIDER_ID, String.class),
                RenderJobLeaseStatus.valueOf(r.get(RENDER_JOB_LEASE.STATUS, String.class)),
                r.get(RENDER_JOB_LEASE.LEASE_VERSION, Long.class),
                toInstant(r.get(RENDER_JOB_LEASE.CLAIMED_AT)),
                toInstant(r.get(RENDER_JOB_LEASE.LEASE_UNTIL)),
                toInstant(r.get(RENDER_JOB_LEASE.RENEWED_AT)),
                toInstant(r.get(RENDER_JOB_LEASE.RELEASED_AT)),
                r.get(RENDER_JOB_LEASE.ATTEMPT, Integer.class),
                r.get(RENDER_JOB_LEASE.MAX_ATTEMPTS, Integer.class),
                r.get(RENDER_JOB_LEASE.HEARTBEAT_TOKEN_HASH, String.class),
                r.get(RENDER_JOB_LEASE.FAILURE_REASON, String.class),
                r.get(RENDER_JOB_LEASE.FAILURE_ERROR_CODE, String.class),
                r.get(RENDER_JOB_LEASE.CREATED_BY_SCHEDULER, String.class),
                toInstant(r.get(RENDER_JOB_LEASE.CREATED_AT)),
                toInstant(r.get(RENDER_JOB_LEASE.UPDATED_AT))
        );
    }

    private static java.time.LocalDateTime toTs(Instant instant) {
        return instant != null ? java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC) : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }
}
