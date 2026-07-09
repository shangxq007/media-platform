package com.example.platform.render.infrastructure;

import com.example.platform.shared.Ids;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

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
        dsl.insertInto(DSL.table("render_job_lifecycle_events"))
                .columns(
                        DSL.field("id"),
                        DSL.field("tenant_id"),
                        DSL.field("project_id"),
                        DSL.field("render_job_id"),
                        DSL.field("event_type"),
                        DSL.field("status_from"),
                        DSL.field("status_to"),
                        DSL.field("worker_id"),
                        DSL.field("attempt"),
                        DSL.field("output_product_id"),
                        DSL.field("reason_code"),
                        DSL.field("reason"),
                        DSL.field("retryable"),
                        DSL.field("duration_ms"),
                        DSL.field("event_time"),
                        DSL.field("created_at"))
                .values(
                        id, tenantId, projectId, renderJobId,
                        eventType, statusFrom, statusTo,
                        workerId, attempt, outputProductId,
                        reasonCode, truncate(reason, 512), retryable,
                        durationMs, OffsetDateTime.now(), OffsetDateTime.now())
                .execute();
    }

    public List<Record> findByRenderJobId(String projectId, String renderJobId, int limit) {
        return dsl.select()
                .from(DSL.table("render_job_lifecycle_events"))
                .where(DSL.field("project_id").eq(projectId)
                        .and(DSL.field("render_job_id").eq(renderJobId)))
                .orderBy(DSL.field("event_time").asc())
                .limit(limit)
                .fetch();
    }

    public int countByRenderJobId(String renderJobId) {
        return dsl.fetchCount(DSL.table("render_job_lifecycle_events"),
                DSL.field("render_job_id").eq(renderJobId));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
