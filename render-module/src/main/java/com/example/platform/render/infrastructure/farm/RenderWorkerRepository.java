package com.example.platform.render.infrastructure.farm;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.RenderWorker.RENDER_WORKER;
import org.jooq.impl.DSL;


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
            dsl.update(RENDER_WORKER)
                    .set(RENDER_WORKER.WORKER_TYPE, reg.workerType())
                    .set(RENDER_WORKER.STATUS, RenderWorkerStatus.STARTING.name())
                    .set(RENDER_WORKER.VERSION, reg.version())
                    .set(RENDER_WORKER.IMAGE_TAG, reg.imageTag())
                    .set(RENDER_WORKER.HOSTNAME, reg.hostname())
                    .set(RENDER_WORKER.ZONE, reg.zone())
                    .set(RENDER_WORKER.PROVIDER_IDS, reg.providerIds())
                    .set(RENDER_WORKER.CAPABILITIES_JSON, reg.capabilitiesJson())
                    .set(RENDER_WORKER.MAX_CONCURRENT_JOBS, reg.maxConcurrentJobs())
                    .set(RENDER_WORKER.CPU_CORES, reg.cpuCores())
                    .set(RENDER_WORKER.MEMORY_MB, reg.memoryMb())
                    .set(RENDER_WORKER.GPU_COUNT, reg.gpuCount())
                    .set(RENDER_WORKER.GPU_TYPE, reg.gpuType())
                    .set(RENDER_WORKER.DISK_FREE_MB, reg.diskFreeMb())
                    .set(RENDER_WORKER.LAST_HEARTBEAT_AT, toTimestamp(now))
                    .set(RENDER_WORKER.EXPIRES_AT, (LocalDateTime) null)
                    .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                    .where(RENDER_WORKER.WORKER_ID.eq(reg.workerId()))
                    .execute();
        } else {
            dsl.insertInto(RENDER_WORKER)
                    .columns(RENDER_WORKER.ID, RENDER_WORKER.WORKER_ID, RENDER_WORKER.WORKER_TYPE, RENDER_WORKER.STATUS,
                            RENDER_WORKER.VERSION, RENDER_WORKER.IMAGE_TAG, RENDER_WORKER.HOSTNAME, RENDER_WORKER.ZONE,
                            RENDER_WORKER.PROVIDER_IDS, RENDER_WORKER.CAPABILITIES_JSON,
                            RENDER_WORKER.MAX_CONCURRENT_JOBS, RENDER_WORKER.ACTIVE_JOB_COUNT,
                            RENDER_WORKER.CPU_CORES, RENDER_WORKER.MEMORY_MB, RENDER_WORKER.GPU_COUNT, RENDER_WORKER.GPU_TYPE,
                            RENDER_WORKER.DISK_FREE_MB, RENDER_WORKER.LAST_HEARTBEAT_AT, RENDER_WORKER.REGISTERED_AT,
                            RENDER_WORKER.CREATED_AT, RENDER_WORKER.UPDATED_AT)
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
        int rows = dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.STATUS, hb.status().name())
                .set(RENDER_WORKER.ACTIVE_JOB_COUNT, hb.activeJobCount())
                .set(RENDER_WORKER.CPU_CORES, hb.cpuCores())
                .set(RENDER_WORKER.MEMORY_MB, hb.memoryMb())
                .set(RENDER_WORKER.GPU_COUNT, hb.gpuCount())
                .set(RENDER_WORKER.GPU_TYPE, hb.gpuType())
                .set(RENDER_WORKER.DISK_FREE_MB, hb.diskFreeMb())
                .set(RENDER_WORKER.METADATA_JSON, hb.metadataJson())
                .set(RENDER_WORKER.LAST_HEARTBEAT_AT, toTimestamp(now))
                .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                .where(RENDER_WORKER.WORKER_ID.eq(hb.workerId()))
                .and(RENDER_WORKER.STATUS.ne(RenderWorkerStatus.OFFLINE.name()))
                .and(RENDER_WORKER.STATUS.ne(RenderWorkerStatus.FAILED.name()))
                .execute();
        return rows > 0;
    }

    /**
     * Mark a worker as IDLE (after registration or job completion).
     */
    public void markIdle(String workerId, Instant now) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.STATUS, RenderWorkerStatus.IDLE.name())
                .set(RENDER_WORKER.EXPIRES_AT, (LocalDateTime) null)
                .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as DRAINING.
     */
    public void markDraining(String workerId, Instant now) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.STATUS, RenderWorkerStatus.DRAINING.name())
                .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as OFFLINE.
     */
    public void markOffline(String workerId, Instant now) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.STATUS, RenderWorkerStatus.OFFLINE.name())
                .set(RENDER_WORKER.EXPIRES_AT, toTimestamp(now))
                .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Mark a worker as FAILED.
     */
    public void markFailed(String workerId, Instant now) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.STATUS, RenderWorkerStatus.FAILED.name())
                .set(RENDER_WORKER.EXPIRES_AT, toTimestamp(now))
                .set(RENDER_WORKER.UPDATED_AT, toTimestamp(now))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Increment active job count for a worker.
     */
    public void incrementActiveJobs(String workerId) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.ACTIVE_JOB_COUNT,
                        RENDER_WORKER.ACTIVE_JOB_COUNT.plus(1))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Decrement active job count for a worker.
     */
    public void decrementActiveJobs(String workerId) {
        dsl.update(RENDER_WORKER)
                .set(RENDER_WORKER.ACTIVE_JOB_COUNT,
                        DSL.when(RENDER_WORKER.ACTIVE_JOB_COUNT.gt(0),
                                RENDER_WORKER.ACTIVE_JOB_COUNT.minus(1))
                        .otherwise(0))
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .execute();
    }

    /**
     * Find a worker by worker_id.
     */
    public Optional<RenderWorkerRecord> findByWorkerId(String workerId) {
        Record r = dsl.select()
                .from(RENDER_WORKER)
                .where(RENDER_WORKER.WORKER_ID.eq(workerId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapRecord);
    }

    /**
     * Find all workers that are IDLE or BUSY (available for job assignment).
     */
    public List<RenderWorkerRecord> findAvailableWorkers() {
        return dsl.select()
                .from(RENDER_WORKER)
                .where(RENDER_WORKER.STATUS.eq(RenderWorkerStatus.IDLE.name())
                        .or(RENDER_WORKER.STATUS.eq(RenderWorkerStatus.BUSY.name())))
                .fetch(this::mapRecord);
    }

    /**
     * Find workers that have not sent a heartbeat since the given threshold.
     */
    public List<RenderWorkerRecord> findStaleWorkers(Instant threshold) {
        return dsl.select()
                .from(RENDER_WORKER)
                .where(RENDER_WORKER.LAST_HEARTBEAT_AT.lt(toTimestamp(threshold)))
                .and(RENDER_WORKER.STATUS.ne(RenderWorkerStatus.OFFLINE.name()))
                .and(RENDER_WORKER.STATUS.ne(RenderWorkerStatus.FAILED.name()))
                .fetch(this::mapRecord);
    }

    /**
     * List all workers.
     */
    public List<RenderWorkerRecord> listAll() {
        return dsl.select()
                .from(RENDER_WORKER)
                .fetch(this::mapRecord);
    }

    private RenderWorkerRecord mapRecord(Record r) {
        return new RenderWorkerRecord(
                r.get(RENDER_WORKER.ID, String.class),
                r.get(RENDER_WORKER.WORKER_ID, String.class),
                RenderWorkerStatus.valueOf(r.get(RENDER_WORKER.STATUS, String.class)),
                r.get(RENDER_WORKER.WORKER_TYPE, String.class),
                r.get(RENDER_WORKER.VERSION, String.class),
                r.get(RENDER_WORKER.IMAGE_TAG, String.class),
                r.get(RENDER_WORKER.HOSTNAME, String.class),
                r.get(RENDER_WORKER.ZONE, String.class),
                r.get(RENDER_WORKER.PROVIDER_IDS, String.class),
                r.get(RENDER_WORKER.CAPABILITIES_JSON, String.class),
                r.get(RENDER_WORKER.MAX_CONCURRENT_JOBS, Integer.class),
                r.get(RENDER_WORKER.ACTIVE_JOB_COUNT, Integer.class),
                r.get(RENDER_WORKER.CPU_CORES, Integer.class),
                r.get(RENDER_WORKER.MEMORY_MB, Integer.class),
                r.get(RENDER_WORKER.GPU_COUNT, Integer.class),
                r.get(RENDER_WORKER.GPU_TYPE, String.class),
                r.get(RENDER_WORKER.DISK_FREE_MB, Long.class),
                toInstant(r.get(RENDER_WORKER.LAST_HEARTBEAT_AT)),
                toInstant(r.get(RENDER_WORKER.REGISTERED_AT)),
                toInstant(r.get(RENDER_WORKER.EXPIRES_AT)),
                r.get(RENDER_WORKER.METADATA_JSON, String.class)
        );
    }

    private static LocalDateTime toTimestamp(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC) : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }
}
