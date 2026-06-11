package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Optional;

/**
 * Render planner that creates a render plan from a render job.
 */
public interface RenderPlanner {

    /**
     * Plan a render job.
     * @param job the render job
     * @return the render plan
     */
    RenderPlan plan(RenderJob job);

    /**
     * Select the best provider for a given capability and job.
     * @param capability the required capability
     * @param job the render job
     * @return the selected provider metadata
     */
    Optional<ProviderMetadata> selectProvider(String capability, RenderJob job);

    /**
     * Get all eligible providers for a job.
     * @param job the render job
     * @return list of eligible provider metadata
     */
    List<ProviderMetadata> getEligibleProviders(RenderJob job);
}
