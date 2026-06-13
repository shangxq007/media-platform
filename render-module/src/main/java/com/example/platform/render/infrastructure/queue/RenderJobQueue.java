package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Minimal job queue backed by database.
 * 
 * <p>Provides deterministic, at-least-once job execution.
 * No distributed consensus complexity - single DB-backed queue.
 */
@Repository
public class RenderJobQueue {

    private static final Logger log = LoggerFactory.getLogger(RenderJobQueue.class);

    private final DSLContext dsl;

    public RenderJobQueue(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Enqueue a job for execution.
     */
    public void enqueue(String jobId, String tenantId, int priority) {
        dsl.insertInto(table("render_job_queue"))
                .columns(
                        field("job_id"),
                        field("tenant_id"),
                        field("status"),
                        field("priority"),
                        field("created_at"),
                        field("updated_at")
                )
                .values(
                        jobId,
                        tenantId,
                        "QUEUED",
                        priority,
                        OffsetDateTime.now(),
                        OffsetDateTime.now()
                )
                .execute();

        log.info("Enqueued job {} with priority {}", jobId, priority);
    }

    /**
     * Dequeue the next available job (FIFO by priority then time).
     * Returns empty if no jobs available.
     */
    public Optional<QueuedJob> dequeue() {
        Record record = dsl.select(
                        field("job_id"),
                        field("tenant_id"),
                        field("priority"),
                        field("created_at")
                )
                .from(table("render_job_queue"))
                .where(field("status").eq("QUEUED"))
                .orderBy(field("priority").desc(), field("created_at").asc())
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        String jobId = record.get(field("job_id", String.class));
        String tenantId = record.get(field("tenant_id", String.class));
        int priority = record.get(field("priority", Integer.class));

        // Mark as dequeued
        dsl.update(table("render_job_queue"))
                .set(field("status"), "DEQUEUED")
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("job_id").eq(jobId))
                .execute();

        log.info("Dequeued job {}", jobId);
        return Optional.of(new QueuedJob(jobId, tenantId, priority));
    }

    /**
     * Mark a job as completed.
     */
    public void complete(String jobId) {
        dsl.update(table("render_job_queue"))
                .set(field("status"), "COMPLETED")
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("job_id").eq(jobId))
                .execute();

        log.info("Completed job {}", jobId);
    }

    /**
     * Mark a job as failed and optionally requeue.
     */
    public void fail(String jobId, boolean requeue) {
        if (requeue) {
            dsl.update(table("render_job_queue"))
                    .set(field("status"), "QUEUED")
                    .set(field("updated_at"), OffsetDateTime.now())
                    .where(field("job_id").eq(jobId))
                    .execute();
            log.info("Requeued failed job {}", jobId);
        } else {
            dsl.update(table("render_job_queue"))
                    .set(field("status"), "FAILED")
                    .set(field("updated_at"), OffsetDateTime.now())
                    .where(field("job_id").eq(jobId))
                    .execute();
            log.info("Failed job {} (no requeue)", jobId);
        }
    }

    /**
     * Get queue statistics.
     */
    public QueueStats getStats() {
        Record record = dsl.select(
                        field("count(*) filter (where status = 'QUEUED')").as("queued"),
                        field("count(*) filter (where status = 'DEQUEUED')").as("processing"),
                        field("count(*) filter (where status = 'COMPLETED')").as("completed"),
                        field("count(*) filter (where status = 'FAILED')").as("failed")
                )
                .from(table("render_job_queue"))
                .fetchOne();

        return new QueueStats(
                record.get(field("queued", Integer.class)),
                record.get(field("processing", Integer.class)),
                record.get(field("completed", Integer.class)),
                record.get(field("failed", Integer.class))
        );
    }

    /**
     * Get a queued job by ID.
     */
    public Optional<QueuedJob> getQueuedJob(String jobId) {
        Record record = dsl.select(
                        field("job_id"),
                        field("tenant_id"),
                        field("priority"),
                        field("status"),
                        field("created_at")
                )
                .from(table("render_job_queue"))
                .where(field("job_id").eq(jobId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new QueuedJob(
                record.get(field("job_id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("priority", Integer.class))
        ));
    }

    /**
     * List all queued jobs.
     */
    public List<QueuedJob> listQueued() {
        return dsl.select(
                        field("job_id"),
                        field("tenant_id"),
                        field("priority")
                )
                .from(table("render_job_queue"))
                .where(field("status").eq("QUEUED"))
                .orderBy(field("priority").desc(), field("created_at").asc())
                .fetch(record -> new QueuedJob(
                        record.get(field("job_id", String.class)),
                        record.get(field("tenant_id", String.class)),
                        record.get(field("priority", Integer.class))
                ));
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record QueuedJob(
            String jobId,
            String tenantId,
            int priority
    ) {}

    public record QueueStats(
            int queued,
            int processing,
            int completed,
            int failed
    ) {
        public int total() {
            return queued + processing + completed + failed;
        }
    }
}
