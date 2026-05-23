package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Loads Internal Timeline 1.0 JSON from a prior render job (ai_script or snapshot).
 */
@Service
public class BaseJobTimelineLoader {

    private final DSLContext dsl;
    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final RenderCacheTenantGuard tenantGuard;

    public BaseJobTimelineLoader(DSLContext dsl,
                                 TimelineSnapshotService timelineSnapshotService,
                                 TimelineSpecResolver timelineSpecResolver,
                                 RenderCacheTenantGuard tenantGuard) {
        this.dsl = dsl;
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.tenantGuard = tenantGuard;
    }

    public Optional<String> loadInternalTimelineJson(String baseJobId, String tenantId) {
        if (baseJobId == null || baseJobId.isBlank()) {
            return Optional.empty();
        }
        if (tenantId != null && !tenantId.isBlank() && tenantGuard != null) {
            try {
                tenantGuard.requireJobTenant(tenantId, baseJobId);
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        Record job = dsl.select(
                        field("tenant_id", String.class),
                        field("ai_script", String.class),
                        field("timeline_snapshot_id", String.class))
                .from(table("render_job"))
                .where(field("id").eq(baseJobId))
                .fetchOne();
        if (job == null) {
            return Optional.empty();
        }
        String aiScript = job.get(field("ai_script", String.class));
        if (aiScript != null && !aiScript.isBlank()
                && timelineSpecResolver.isInternalTimelineJson(aiScript)) {
            return Optional.of(aiScript.trim());
        }
        String snapshotId = job.get(field("timeline_snapshot_id", String.class));
        return timelineSnapshotService.findPayload(snapshotId)
                .filter(payload -> !payload.isBlank())
                .filter(timelineSpecResolver::isInternalTimelineJson)
                .map(String::trim);
    }
}
