package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobLease.RENDER_JOB_LEASE;


/**
 * Job lease system to prevent double execution.
 * 
 * <p>Workers must acquire a lease before executing a job.
 * Leases expire after a timeout to prevent deadlocks.
 */
@Repository
public class JobLeaseRepository {

    private static final Logger log = LoggerFactory.getLogger(JobLeaseRepository.class);

    private static final long LEASE_DURATION_MS = 300_000; // 5 minutes
    private static final int MAX_ATTEMPTS = 3;

    private final DSLContext dsl;

    public JobLeaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Try to acquire a lease for a job.
     * Returns empty if lease cannot be acquired.
     */
    public Optional<JobLease> acquireLease(String jobId, String workerId) {
        // Check if job already has an active lease
        Record existing = dsl.select(
                        RENDER_JOB_LEASE.LEASE_ID,
                        RENDER_JOB_LEASE.STATUS,
                        RENDER_JOB_LEASE.LEASE_UNTIL
                )
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.JOB_ID.eq(jobId))
                .and(RENDER_JOB_LEASE.STATUS.eq("ACTIVE"))
                .fetchOne();

        if (existing != null) {
            LocalDateTime leaseUntil = existing.get(RENDER_JOB_LEASE.LEASE_UNTIL);
            if (leaseUntil != null && leaseUntil.isAfter(LocalDateTime.now())) {
                // Active lease exists
                log.debug("Job {} already has active lease", jobId);
                return Optional.empty();
            }
        }

        // Create new lease
        String leaseId = "lease-" + jobId + "-" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusNanos(LEASE_DURATION_MS * 1_000_000);

        dsl.insertInto(RENDER_JOB_LEASE)
                .columns(
                        RENDER_JOB_LEASE.ID,
                        RENDER_JOB_LEASE.LEASE_ID,
                        RENDER_JOB_LEASE.JOB_ID,
                        RENDER_JOB_LEASE.WORKER_ID,
                        RENDER_JOB_LEASE.STATUS,
                        RENDER_JOB_LEASE.LEASE_VERSION,
                        RENDER_JOB_LEASE.CLAIMED_AT,
                        RENDER_JOB_LEASE.LEASE_UNTIL,
                        RENDER_JOB_LEASE.ATTEMPT,
                        RENDER_JOB_LEASE.MAX_ATTEMPTS,
                        RENDER_JOB_LEASE.CREATED_AT,
                        RENDER_JOB_LEASE.UPDATED_AT
                )
                .values(
                        leaseId,
                        leaseId,
                        jobId,
                        workerId,
                        "ACTIVE",
                        1L,
                        now,
                        leaseUntil,
                        1,
                        MAX_ATTEMPTS,
                        now,
                        now
                )
                .execute();

        log.info("Acquired lease {} for job {} by worker {}", leaseId, jobId, workerId);
        return Optional.of(new JobLease(leaseId, jobId, workerId, leaseUntil.atOffset(ZoneOffset.UTC).toInstant()));
    }

    /**
     * Release a lease (job completed or failed).
     */
    public void releaseLease(String leaseId, String status) {
        dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, status)
                .set(RENDER_JOB_LEASE.RELEASED_AT, LocalDateTime.now())
                .set(RENDER_JOB_LEASE.UPDATED_AT, LocalDateTime.now())
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .execute();

        log.info("Released lease {} with status {}", leaseId, status);
    }

    /**
     * Renew a lease (worker still active).
     */
    public boolean renewLease(String leaseId) {
        LocalDateTime newUntil = LocalDateTime.now().plusNanos(LEASE_DURATION_MS * 1_000_000);

        int updated = dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.LEASE_UNTIL, newUntil)
                .set(RENDER_JOB_LEASE.RENEWED_AT, LocalDateTime.now())
                .set(RENDER_JOB_LEASE.UPDATED_AT, LocalDateTime.now())
                .where(RENDER_JOB_LEASE.LEASE_ID.eq(leaseId))
                .and(RENDER_JOB_LEASE.STATUS.eq("ACTIVE"))
                .execute();

        return updated > 0;
    }

    /**
     * Get lease for a job.
     */
    public Optional<JobLease> getLease(String jobId) {
        Record record = dsl.select(
                        RENDER_JOB_LEASE.LEASE_ID,
                        RENDER_JOB_LEASE.JOB_ID,
                        RENDER_JOB_LEASE.WORKER_ID,
                        RENDER_JOB_LEASE.LEASE_UNTIL,
                        RENDER_JOB_LEASE.STATUS
                )
                .from(RENDER_JOB_LEASE)
                .where(RENDER_JOB_LEASE.JOB_ID.eq(jobId))
                .and(RENDER_JOB_LEASE.STATUS.eq("ACTIVE"))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new JobLease(
                record.get(RENDER_JOB_LEASE.LEASE_ID),
                record.get(RENDER_JOB_LEASE.JOB_ID),
                record.get(RENDER_JOB_LEASE.WORKER_ID),
                record.get(RENDER_JOB_LEASE.LEASE_UNTIL).atOffset(ZoneOffset.UTC).toInstant()
        ));
    }

    /**
     * Check if a job has an active lease.
     */
    public boolean hasActiveLease(String jobId) {
        return getLease(jobId).isPresent();
    }

    /**
     * Expire stale leases.
     */
    public int expireStaleLeases() {
        return dsl.update(RENDER_JOB_LEASE)
                .set(RENDER_JOB_LEASE.STATUS, "EXPIRED")
                .set(RENDER_JOB_LEASE.UPDATED_AT, LocalDateTime.now())
                .where(RENDER_JOB_LEASE.STATUS.eq("ACTIVE"))
                .and(RENDER_JOB_LEASE.LEASE_UNTIL.lt(LocalDateTime.now()))
                .execute();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record JobLease(
            String leaseId,
            String jobId,
            String workerId,
            Instant leaseUntil
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(leaseUntil);
        }
    }
}
