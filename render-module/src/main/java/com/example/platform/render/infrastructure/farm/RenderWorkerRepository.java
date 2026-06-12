package com.example.platform.render.infrastructure.farm;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code render_worker} table.
 */
@Repository
public class RenderWorkerRepository {

    private final DSLContext dsl;

    public RenderWorkerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Upsert a worker registration. If the worker already exists (by worker_id),
     * update its metadata and reset status to STARTING.
     */
    public void register(RenderWorkerRegistration reg, Instant now) {
        Optional<RenderWorkerRecord> existing = findByWorkerId(reg.workerId());
        if (existing.isPresent()) {
            dsl.update(table("render_worker"))
                    .set(field("worker_type"), reg.workerType())
                    .set(field("status"), RenderWorkerStatus.STARTING.name())
                    .set(field("version"), reg.version())
                    .set(field("image_tag"), reg.imageTag())
                    .set(field("hostname"), reg.hostname())
                    .set(field("zone"), reg.zone())
                    .set(field("provider_ids"), reg.providerIds())
                    .set(field("capabilities_json"), reg.capabilitiesJson())
                    .set(field("max_concurrent_jobs"), reg.maxConcurrentJobs())
                    .set(field("cpu_cores"), reg.cpuCores())
                    .set(field("memory_mb"), reg.memoryMb())
                    .set(field("gpu_count"), reg.gpuCount())
                    .set(field("gpu_type"), reg.gpuType())
                    .set(field("disk_free_mb"), reg.diskFreeMb())
                    .set(field("last_heartbeat_at"), toTimestamp(now))
                    .set(field("expires_at"), (OffsetDateTime) null)
                    .set(field("updated_at"), toTimestamp(now))
                    .where(field("worker_id").eq(reg.workerId()))
                    .execute();
        } else {
            dsl.insertInto(table("render_worker"))
                    .columns(field("id"), field("worker_id"), field("worker_type"), field("status"),
                            field("version"), field("image_tag"), field("hostname"), field("zone"),
                            field("provider_ids"), field("capabilities_json"),
                            field("max_concurrent_jobs"), field("active_job_count"),
                            field("cpu_cores"), field("memory_mb"), field("gpu_count"), field("gpu_type"),
                            field("disk_free_mb"), field("last_heartbeat_at"), field("registered_at"),
                            field("created_at"), field("updated_at"))
                    .values(reg.workerId(), reg.workerId(), reg.workerType(),
                            RenderWorkerStatus.STARTING.name(),
                            reg.version(), reg.imageTag(), reg.hostname(), reg.zone(),
                            reg.providerIds(), reg.capabilitiesJson(),
                            reg.maxConcurrentJobs(), 0,
                            reg.cpuCores(), reg.memoryMb(), reg.gpuCount(), reg.gpuType(),
                            reg.diskFreeMb(), toTimestamp(now), toTimestamp(now),
                            toTimestamp(now), toTimestamp(now))
                    .execute();
        }
    }

    /**
     * Update worker heartbeat. Only updates if worker is not OFFLINE/FAILED.
     */
    public boolean heartbeat(RenderWorkerHeartbeat hb, Instant now) {
        int rows = dsl.update(table("render_worker"))
                .set(field("status"), hb.status().name())
                .set(field("active_job_count"), hb.activeJobCount())
                .set(field("cpu_cores"), hb.cpuCores())
                .set(field("memory_mb"), hb.memoryMb())
                .set(field("gpu_count"), hb.gpuCount())
                .set(field("gpu_type"), hb.gpuType())
                .set(field("disk_free_mb"), hb.diskFreeMb())
                .set(field("metadata_json"), hb.metadataJson())
                .set(field("last_heartbeat_at"), toTimestamp(now))
                .set(field("updated_at"), toTimestamp(now))
                .where(field("worker_id").eq(hb.workerId()))
                .and(field("status").ne(RenderWorkerStatus.OFFLINE.name()))
                .and(field("status").ne(RenderWorkerStatus.FAILED.name()))
                .execute();
        return rows > 0;
    }

    /**
     * Mark a worker as IDLE (after registration or job completion).
     */
    public void markIdle(String workerId, Instant now) {
        dsl.update(table("render_worker"))
                .set(field("status"), RenderWorkerStatus.IDLE.name())
                .set(field("expires_at"), (OffsetDateTime) null)
                .set(field("updated_at"), toTimestamp(now))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as DRAINING.
     */
    public void markDraining(String workerId, Instant now) {
        dsl.update(table("render_worker"))
                .set(field("status"), RenderWorkerStatus.DRAINING.name())
                .set(field("updated_at"), toTimestamp(now))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as OFFLINE.
     */
    public void markOffline(String workerId, Instant now) {
        dsl.update(table("render_worker"))
                .set(field("status"), RenderWorkerStatus.OFFLINE.name())
                .set(field("expires_at"), toTimestamp(now))
                .set(field("updated_at"), toTimestamp(now))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as FAILED.
     */
    public void markFailed(String workerId, Instant now) {
        dsl.update(table("render_worker"))
                .set(field("status"), RenderWorkerStatus.FAILED.name())
                .set(field("expires_at"), toTimestamp(now))
                .set(field("updated_at"), toTimestamp(now))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Increment active job count for a worker.
     */
    public void incrementActiveJobs(String workerId) {
        dsl.update(table("render_worker"))
                .set(field("active_job_count", Integer.class),
                        org.jooq.impl.DSL.field("active_job_count", Integer.class).plus(1))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Decrement active job count for a worker.
     */
    public void decrementActiveJobs(String workerId) {
        dsl.update(table("render_worker"))
                .set(field("active_job_count", Integer.class),
                        org.jooq.impl.DSL.when(
                                org.jooq.impl.DSL.field("active_job_count", Integer.class).gt(0),
                                org.jooq.impl.DSL.field("active_job_count", Integer.class).minus(1))
                        .otherwise(0))
                .where(field("worker_id").eq(workerId))
                .execute();
    }

    /**
     * Find a worker by worker_id.
     */
    public Optional<RenderWorkerRecord> findByWorkerId(String workerId) {
        Record r = dsl.select()
                .from(table("render_worker"))
                .where(field("worker_id").eq(workerId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapRecord);
    }

    /**
     * Find all workers that are IDLE or BUSY (available for job assignment).
     */
    public List<RenderWorkerRecord> findAvailableWorkers() {
        return dsl.select()
                .from(table("render_worker"))
                .where(field("status").eq(RenderWorkerStatus.IDLE.name())
                        .or(field("status").eq(RenderWorkerStatus.BUSY.name())))
                .fetch(this::mapRecord);
    }

    /**
     * Find workers that have not sent a heartbeat since the given threshold.
     */
    public List<RenderWorkerRecord> findStaleWorkers(Instant threshold) {
        return dsl.select()
                .from(table("render_worker"))
                .where(field("last_heartbeat_at").lt(toTimestamp(threshold)))
                .and(field("status").ne(RenderWorkerStatus.OFFLINE.name()))
                .and(field("status").ne(RenderWorkerStatus.FAILED.name()))
                .fetch(this::mapRecord);
    }

    /**
     * List all workers.
     */
    public List<RenderWorkerRecord> listAll() {
        return dsl.select()
                .from(table("render_worker"))
                .fetch(this::mapRecord);
    }

    private RenderWorkerRecord mapRecord(Record r) {
        return new RenderWorkerRecord(
                r.get(field("id"), String.class),
                r.get(field("worker_id"), String.class),
                RenderWorkerStatus.valueOf(r.get(field("status"), String.class)),
                r.get(field("worker_type"), String.class),
                r.get(field("version"), String.class),
                r.get(field("image_tag"), String.class),
                r.get(field("hostname"), String.class),
                r.get(field("zone"), String.class),
                r.get(field("provider_ids"), String.class),
                r.get(field("capabilities_json"), String.class),
                r.get(field("max_concurrent_jobs"), Integer.class),
                r.get(field("active_job_count") != null ? field("active_job_count") : field("active_job_count"), Integer.class),
                r.get(field("cpu_cores"), Integer.class),
                r.get(field("memory_mb"), Integer.class),
                r.get(field("gpu_count") != null ? field("gpu_count") : field("gpu_count"), Integer.class),
                r.get(field("gpu_type"), String.class),
                r.get(field("disk_free_mb") != null ? field("disk_free_mb") : field("disk_free_mb"), Long.class),
                toInstant(r.get(field("last_heartbeat_at"))),
                toInstant(r.get(field("registered_at"))),
                toInstant(r.get(field("expires_at"))),
                r.get(field("metadata_json"), String.class)
        );
    }

    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant != null ? OffsetDateTime.from(instant.atOffset(java.time.ZoneOffset.UTC)) : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }
}
