package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Packaging provider interface.
 * Suitable for: GPACPackagingProvider
 * Responsibilities: package_hls, package_dash, package_cmaf
 */
public interface PackagingProvider extends BaseProvider {

    /**
     * Package a streaming delivery job.
     * @param job the packaging job
     * @return the packaging result
     */
    PackagingResult packageJob(PackagingJob job);

    /**
     * Packaging job definition.
     */
    interface PackagingJob extends ProviderJob {
        String inputUri();
        String outputUri();
        String format();
        String resolution();
        int segmentDurationSec();
        boolean multiBitrate();
    }

    /**
     * Packaging result.
     */
    record PackagingResult(
            String artifactId,
            String manifestUri,
            String format,
            String resolution,
            boolean success
    ) {}
}
