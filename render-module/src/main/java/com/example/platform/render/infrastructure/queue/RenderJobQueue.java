package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobQueue.RENDER_JOB_QUEUE;
import org.jooq.impl.DSL;


/**
 * Minimal job queue backed by database.
 * 
 * <p>Provides deterministic, at-least-once job execution.
 * No distributed consensus complexity - single DB-backed queue.
 */
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
        dsl.insertInto(RENDER_JOB_QUEUE)
                .columns(
                        RENDER_JOB_QUEUE.JOB_ID,
                        RENDER_JOB_QUEUE.TENANT_ID,
                        RENDER_JOB_QUEUE.STATUS,
                        RENDER_JOB_QUEUE.PRIORITY,
                        RENDER_JOB_QUEUE.CREATED_AT,
                        RENDER_JOB_QUEUE.UPDATED_AT
                )
                .values(
                        jobId,
                        tenantId,
                        "QUEUED",
                        priority,
                        LocalDateTime.now(),
                        LocalDateTime.now()
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
                        RENDER_JOB_QUEUE.JOB_ID,
                        RENDER_JOB_QUEUE.TENANT_ID,
                        RENDER_JOB_QUEUE.PRIORITY,
                        RENDER_JOB_QUEUE.CREATED_AT
                )
                .from(RENDER_JOB_QUEUE)
                .where(RENDER_JOB_QUEUE.STATUS.eq("QUEUED"))
                .orderBy(RENDER_JOB_QUEUE.PRIORITY.desc(), RENDER_JOB_QUEUE.CREATED_AT.asc())
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        String jobId = record.get(RENDER_JOB_QUEUE.JOB_ID);
        String tenantId = record.get(RENDER_JOB_QUEUE.TENANT_ID);
        int priority = record.get(RENDER_JOB_QUEUE.PRIORITY);

        // Mark as dequeued
        dsl.update(RENDER_JOB_QUEUE)
                .set(RENDER_JOB_QUEUE.STATUS, "DEQUEUED")
                .set(RENDER_JOB_QUEUE.UPDATED_AT, LocalDateTime.now())
                .where(RENDER_JOB_QUEUE.JOB_ID.eq(jobId))
                .execute();

        log.info("Dequeued job {}", jobId);
        return Optional.of(new QueuedJob(jobId, tenantId, priority));
    }

    /**
     * Mark a job as completed.
     */
    public void complete(String jobId) {
        dsl.update(RENDER_JOB_QUEUE)
                .set(RENDER_JOB_QUEUE.STATUS, "COMPLETED")
                .set(RENDER_JOB_QUEUE.UPDATED_AT, LocalDateTime.now())
                .where(RENDER_JOB_QUEUE.JOB_ID.eq(jobId))
                .execute();

        log.info("Completed job {}", jobId);
    }

    /**
     * Mark a job as failed and optionally requeue.
     */
    public void fail(String jobId, boolean requeue) {
        if (requeue) {
            dsl.update(RENDER_JOB_QUEUE)
                    .set(RENDER_JOB_QUEUE.STATUS, "QUEUED")
                    .set(RENDER_JOB_QUEUE.UPDATED_AT, LocalDateTime.now())
                    .where(RENDER_JOB_QUEUE.JOB_ID.eq(jobId))
                    .execute();
            log.info("Requeued failed job {}", jobId);
        } else {
            dsl.update(RENDER_JOB_QUEUE)
                    .set(RENDER_JOB_QUEUE.STATUS, "FAILED")
                    .set(RENDER_JOB_QUEUE.UPDATED_AT, LocalDateTime.now())
                    .where(RENDER_JOB_QUEUE.JOB_ID.eq(jobId))
                    .execute();
            log.info("Failed job {} (no requeue)", jobId);
        }
    }

    /**
     * Get queue statistics.
     */
    public QueueStats getStats() {
        var queuedField = DSL.field(DSL.raw("count(*) filter (where status = 'QUEUED')"), Integer.class).as("queued");
        var processingField = DSL.field(DSL.raw("count(*) filter (where status = 'DEQUEUED')"), Integer.class).as("processing");
        var completedField = DSL.field(DSL.raw("count(*) filter (where status = 'COMPLETED')"), Integer.class).as("completed");
        var failedField = DSL.field(DSL.raw("count(*) filter (where status = 'FAILED')"), Integer.class).as("failed");
        Record record = dsl.select(
                        queuedField,
                        processingField,
                        completedField,
                        failedField
                )
                .from(RENDER_JOB_QUEUE)
                .fetchOne();

        return new QueueStats(
                record.get(queuedField),
                record.get(processingField),
                record.get(completedField),
                record.get(failedField)
        );
    }

    /**
     * Get a queued job by ID.
     */
    public Optional<QueuedJob> getQueuedJob(String jobId) {
        Record record = dsl.select(
                        RENDER_JOB_QUEUE.JOB_ID,
                        RENDER_JOB_QUEUE.TENANT_ID,
                        RENDER_JOB_QUEUE.PRIORITY,
                        RENDER_JOB_QUEUE.STATUS,
                        RENDER_JOB_QUEUE.CREATED_AT
                )
                .from(RENDER_JOB_QUEUE)
                .where(RENDER_JOB_QUEUE.JOB_ID.eq(jobId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }

        return Optional.of(new QueuedJob(
                record.get(RENDER_JOB_QUEUE.JOB_ID),
                record.get(RENDER_JOB_QUEUE.TENANT_ID),
                record.get(RENDER_JOB_QUEUE.PRIORITY)
        ));
    }

    /**
     * List all queued jobs.
     */
    public List<QueuedJob> listQueued() {
        return dsl.select(
                        RENDER_JOB_QUEUE.JOB_ID,
                        RENDER_JOB_QUEUE.TENANT_ID,
                        RENDER_JOB_QUEUE.PRIORITY
                )
                .from(RENDER_JOB_QUEUE)
                .where(RENDER_JOB_QUEUE.STATUS.eq("QUEUED"))
                .orderBy(RENDER_JOB_QUEUE.PRIORITY.desc(), RENDER_JOB_QUEUE.CREATED_AT.asc())
                .fetch(record -> new QueuedJob(
                        record.get(RENDER_JOB_QUEUE.JOB_ID),
                        record.get(RENDER_JOB_QUEUE.TENANT_ID),
                        record.get(RENDER_JOB_QUEUE.PRIORITY)
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
