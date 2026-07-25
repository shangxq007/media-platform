package com.example.platform.render.infrastructure;

import com.example.platform.shared.Ids;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobLifecycleEvents.RENDER_JOB_LIFECYCLE_EVENTS;


/**
 * Repository for persisted RenderJob lifecycle events.
 */
@Repository
public class RenderJobLifecycleEventRepository {

    private final DSLContext dsl;

    public RenderJobLifecycleEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void append(String tenantId, String projectId, String renderJobId,
                       String eventType, String statusFrom, String statusTo,
                       String workerId, int attempt, String outputProductId,
                       String reasonCode, String reason, boolean retryable,
                       Long durationMs) {
        String id = Ids.newId("evt");
        dsl.insertInto(RENDER_JOB_LIFECYCLE_EVENTS)
                .columns(
                        RENDER_JOB.ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.PROJECT_ID,
                        RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID,
                        RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TYPE,
                        RENDER_JOB_LIFECYCLE_EVENTS.STATUS_FROM,
                        RENDER_JOB_LIFECYCLE_EVENTS.STATUS_TO,
                        RENDER_JOB_LIFECYCLE_EVENTS.WORKER_ID,
                        RENDER_JOB_LIFECYCLE_EVENTS.ATTEMPT,
                        RENDER_JOB_LIFECYCLE_EVENTS.OUTPUT_PRODUCT_ID,
                        RENDER_JOB_LIFECYCLE_EVENTS.REASON_CODE,
                        RENDER_JOB_LIFECYCLE_EVENTS.REASON,
                        RENDER_JOB_LIFECYCLE_EVENTS.RETRYABLE,
                        RENDER_JOB_LIFECYCLE_EVENTS.DURATION_MS,
                        RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TIME,
                        RENDER_JOB.CREATED_AT)
                .values(
                        id, tenantId, projectId, renderJobId,
                        eventType, statusFrom, statusTo,
                        workerId, attempt, outputProductId,
                        reasonCode, truncate(reason, 512), retryable,
                        durationMs, LocalDateTime.now(), LocalDateTime.now())
                .execute();
    }

    public List<Record> findByRenderJobId(String projectId, String renderJobId, int limit) {
        return dsl.select()
                .from(RENDER_JOB_LIFECYCLE_EVENTS)
                .where(RENDER_JOB.PROJECT_ID.eq(projectId)
                        .and(RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID.eq(renderJobId)))
                .orderBy(RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TIME.asc())
                .limit(limit)
                .fetch();
    }

    public int countByRenderJobId(String renderJobId) {
        return dsl.fetchCount(RENDER_JOB_LIFECYCLE_EVENTS,
                RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID.eq(renderJobId));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }


    // === Retention / Cleanup ===

    public int countEventsOlderThan(java.time.Instant cutoff) {
        return dsl.fetchCount(RENDER_JOB_LIFECYCLE_EVENTS,
                RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TIME.lessThan(java.sql.Timestamp.from(cutoff).toLocalDateTime()));
    }

    public java.time.Instant findOldestEventTime() {
        LocalDateTime ts = dsl.select(DSL.min(RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TIME))
                .from(RENDER_JOB_LIFECYCLE_EVENTS)
                .fetchOneInto(LocalDateTime.class);
        return ts != null ? ts.atOffset(java.time.ZoneOffset.UTC).toInstant() : null;
    }

    public int deleteEventsOlderThan(java.time.Instant cutoff, int batchSize) {
        // Delete events older than cutoff, excluding active EXECUTING jobs
        return dsl.deleteFrom(RENDER_JOB_LIFECYCLE_EVENTS)
                .where(RENDER_JOB_LIFECYCLE_EVENTS.EVENT_TIME.lessThan(java.sql.Timestamp.from(cutoff).toLocalDateTime())
                        .and(RENDER_JOB_LIFECYCLE_EVENTS.RENDER_JOB_ID.notIn(
                                dsl.select(RENDER_JOB.ID)
                                        .from(RENDER_JOB)
                                        .where(RENDER_JOB.STATUS.eq("EXECUTING")))))
                .limit(batchSize)
                .execute();
    }
}
