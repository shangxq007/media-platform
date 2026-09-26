package com.example.platform.render.app.timeline;

import com.example.platform.timeline.api.revision.TimelineSnapshotView;
import com.example.platform.timeline.api.revision.TimelineSnapshotQueries;
import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderJobRepository.TimelineData;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Loads Internal Timeline 1.0 JSON from a prior render job (ai_script or snapshot).
 *
 * <p>This service uses {@link RenderJobRepository} for all render_job access —
 * no inline jOOQ.
 */
@Service
public class BaseJobTimelineLoader {

    private final RenderJobRepository renderJobRepository;
    private final TimelineSnapshotQueries timelineSnapshotService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final RenderCacheTenantGuard tenantGuard;

    public BaseJobTimelineLoader(RenderJobRepository renderJobRepository,
                                 TimelineSnapshotQueries timelineSnapshotService,
                                 TimelineSpecResolver timelineSpecResolver,
                                 RenderCacheTenantGuard tenantGuard) {
        this.renderJobRepository = renderJobRepository;
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.tenantGuard = tenantGuard;
    }

    public Optional<String> loadInternalTimelineJson(String baseJobId, String tenantId) {
        return load(baseJobId, tenantId, null);
    }

    /**
     * PROJECT-SCOPED load (AUTH-IDOR-FIX-001): the addressed base job must belong to
     * {@code projectId}. A base job owned by another project in the same tenant is treated as
     * absent (fail closed), so an actor authorized for project A cannot read project B's job
     * timeline through a free {@code baseJobId} input.
     */
    public Optional<String> loadInternalTimelineJson(String baseJobId, String tenantId, String projectId) {
        return load(baseJobId, tenantId, projectId);
    }

    private Optional<String> load(String baseJobId, String tenantId, String projectId) {
        if (baseJobId == null || baseJobId.isBlank()) {
            return Optional.empty();
        }
        if (tenantId != null && !tenantId.isBlank() && tenantGuard != null) {
            try {
                if (projectId != null) {
                    if (projectId.isBlank()) {
                        // Fail closed: a project-scoped load without a usable project must not
                        // silently degrade to a tenant-wide read.
                        return Optional.empty();
                    }
                    // requireJobAccess binds job -> project (throws on mismatch).
                    tenantGuard.requireJobAccess(tenantId, projectId, baseJobId);
                } else {
                    tenantGuard.requireJobTenant(tenantId, baseJobId);
                }
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        }
        Optional<TimelineData> jobOpt = renderJobRepository.findTimelineDataById(baseJobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }
        TimelineData job = jobOpt.get();
        String aiScript = job.aiScript();
        if (aiScript != null && !aiScript.isBlank()
                && timelineSpecResolver.isInternalTimelineJson(aiScript)) {
            return Optional.of(aiScript.trim());
        }
        String snapshotId = job.timelineSnapshotId();
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        // CFRH-I2: ownership-scoped snapshot read — render_job.PROJECT_ID (authoritative)
        // threaded to findOwnedById(projectId, tenantId, snapshotId). No ambient-global
        // findPayload. Tenant source: TimelineData.tenantId (render_job.TENANT_ID).
        return timelineSnapshotService
                .findOwnedById(job.projectId(), job.tenantId(), snapshotId)
                .map(TimelineSnapshotView::payloadJson)
                .filter(payload -> !payload.isBlank())
                .filter(timelineSpecResolver::isInternalTimelineJson)
                .map(String::trim);
    }
}
