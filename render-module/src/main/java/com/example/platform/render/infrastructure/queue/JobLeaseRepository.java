package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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
                        field("lease_id"),
                        field("status"),
                        field("lease_until")
                )
                .from(table("render_job_lease"))
                .where(field("job_id").eq(jobId))
                .and(field("status").eq("ACTIVE"))
                .fetchOne();

        if (existing != null) {
            OffsetDateTime leaseUntil = existing.get(field("lease_until", OffsetDateTime.class));
            if (leaseUntil != null && leaseUntil.isAfter(OffsetDateTime.now())) {
                // Active lease exists
                log.debug("Job {} already has active lease", jobId);
                return Optional.empty();
            }
        }

        // Create new lease
        String leaseId = "lease-" + jobId + "-" + System.currentTimeMillis();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseUntil = now.plusNanos(LEASE_DURATION_MS * 1_000_000);

        dsl.insertInto(table("render_job_lease"))
                .columns(
                        field("id"),
                        field("lease_id"),
                        field("job_id"),
                        field("worker_id"),
                        field("status"),
                        field("lease_version"),
                        field("claimed_at"),
                        field("lease_until"),
                        field("attempt"),
                        field("max_attempts"),
                        field("created_at"),
                        field("updated_at")
                )
                .values(
                        leaseId,
                        leaseId,
                        jobId,
                        workerId,
                        "ACTIVE",
                        1,
                        now,
                        leaseUntil,
                        1,
                        MAX_ATTEMPTS,
                        now,
                        now
                )
                .execute();

        log.info("Acquired lease {} for job {} by worker {}", leaseId, jobId, workerId);
        return Optional.of(new JobLease(leaseId, jobId, workerId, leaseUntil.toInstant()));
    }

    /**
     * Release a lease (job completed or failed).
     */
    public void releaseLease(String leaseId, String status) {
        dsl.update(table("render_job_lease"))
                .set(field("status"), status)
                .set(field("released_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("lease_id").eq(leaseId))
                .execute();

        log.info("Released lease {} with status {}", leaseId, status);
    }

    /**
     * Renew a lease (worker still active).
     */
    public boolean renewLease(String leaseId) {
        OffsetDateTime newUntil = OffsetDateTime.now().plusNanos(LEASE_DURATION_MS * 1_000_000);

        int updated = dsl.update(table("render_job_lease"))
                .set(field("lease_until"), newUntil)
                .set(field("renewed_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("lease_id").eq(leaseId))
                .and(field("status").eq("ACTIVE"))
                .execute();

        return updated > 0;
    }

    /**
     * Get lease for a job.
     */
    public Optional<JobLease> getLease(String jobId) {
        Record record = dsl.select(
                        field("lease_id"),
                        field("job_id"),
                        field("worker_id"),
                        field("lease_until"),
                        field("status")
                )
                .from(table("render_job_lease"))
                .where(field("job_id").eq(jobId))
                .and(field("status").eq("ACTIVE"))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new JobLease(
                record.get(field("lease_id", String.class)),
                record.get(field("job_id", String.class)),
                record.get(field("worker_id", String.class)),
                record.get(field("lease_until", OffsetDateTime.class)).toInstant()
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
        return dsl.update(table("render_job_lease"))
                .set(field("status"), "EXPIRED")
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("status").eq("ACTIVE"))
                .and(field("lease_until").lt(OffsetDateTime.now()))
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
