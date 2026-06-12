package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
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
    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final RenderCacheTenantGuard tenantGuard;

    public BaseJobTimelineLoader(RenderJobRepository renderJobRepository,
                                 TimelineSnapshotService timelineSnapshotService,
                                 TimelineSpecResolver timelineSpecResolver,
                                 RenderCacheTenantGuard tenantGuard) {
        this.renderJobRepository = renderJobRepository;
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
        return timelineSnapshotService.findPayload(snapshotId)
                .filter(payload -> !payload.isBlank())
                .filter(timelineSpecResolver::isInternalTimelineJson)
                .map(String::trim);
    }
}
