package com.example.platform.render.app;

import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.web.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Loads the best available timeline JSON for a render job.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Base job timeline (from {@link BaseJobTimelineLoader})</li>
 *   <li>ai_script persisted on the render job (via {@link RenderJobRepository})</li>
 *   <li>Empty string if nothing available</li>
 * </ol>
 *
 * <p>This service does NOT use inline jOOQ — all render_job access goes through
 * {@link RenderJobRepository}.
 */
@Service
public class RenderJobTimelineQueryService {

    private final RenderJobRepository renderJobRepository;
    private final BaseJobTimelineLoader baseJobTimelineLoader;
    private final RenderCacheTenantGuard cacheTenantGuard;

    public RenderJobTimelineQueryService(
            RenderJobRepository renderJobRepository,
            BaseJobTimelineLoader baseJobTimelineLoader,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RenderCacheTenantGuard cacheTenantGuard) {
        this.renderJobRepository = renderJobRepository;
        this.baseJobTimelineLoader = baseJobTimelineLoader;
        this.cacheTenantGuard = cacheTenantGuard;
    }

    /**
     * Load the best available Internal Timeline 1.0 JSON for a render job.
     *
     * @param tenantId the tenant requesting the timeline (for access control)
     * @param jobId    the render job ID
     * @return the timeline JSON, or empty string if not available
     * @throws IllegalArgumentException if tenant access is denied
     */
    public String loadJobTimelineJson(String tenantId, String jobId) {
        assertTenantAccess(tenantId);
        if (cacheTenantGuard != null) {
            cacheTenantGuard.requireJobTenant(tenantId, jobId);
        }
        return baseJobTimelineLoader.loadInternalTimelineJson(jobId, tenantId)
                .orElseGet(() -> renderJobRepository.findAiScriptById(jobId).orElse(""));
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }
}
