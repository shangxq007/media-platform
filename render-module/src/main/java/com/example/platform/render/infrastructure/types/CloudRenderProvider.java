package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Cloud render provider interface.
 * Suitable for: ShotstackRenderProvider
 * Responsibilities: cloud_render, external_render, remote_job_submit
 */
public interface CloudRenderProvider extends BaseProvider {

    /**
     * Submit a remote render job.
     * @param job the cloud render job
     * @return the cloud render result
     */
    CloudRenderResult submitRemoteJob(CloudRenderJob job);

    /**
     * Cloud render job definition.
     */
    interface CloudRenderJob extends ProviderJob {
        String templateId();
        String timelineJson();
        String outputUri();
        String callbackUri();
    }

    /**
     * Cloud render result.
     */
    record CloudRenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success,
            String externalJobId
    ) {}
}
