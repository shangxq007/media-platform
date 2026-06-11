package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Utility provider interface.
 * Suitable for: JavaCVUtilityProvider
 * Responsibilities: JVM-internal tool capabilities, metadata read, frame extraction, OpenCV frame processing
 * NOT for top-level scheduling.
 */
public interface UtilityProvider extends BaseProvider {

    /**
     * Execute a utility job.
     * @param job the utility job
     * @return the utility result
     */
    UtilityResult executeUtility(UtilityJob job);

    /**
     * Utility job definition.
     */
    interface UtilityJob extends ProviderJob {
        String utilityType();
        String inputUri();
        String paramsJson();
    }

    /**
     * Utility result.
     */
    record UtilityResult(
            String utilityType,
            String outputJson,
            boolean success,
            String errorMessage
    ) {}
}
