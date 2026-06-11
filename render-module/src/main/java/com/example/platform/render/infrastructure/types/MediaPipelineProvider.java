package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Media pipeline provider interface.
 * Suitable for: BMFMediaPipelineProvider
 * Responsibilities: media_pipeline, ai_media_pipeline, graph_based_processing, preprocess, thumbnail, transcode
 */
public interface MediaPipelineProvider extends BaseProvider {

    /**
     * Run a media pipeline job.
     * @param job the media pipeline job
     * @return the pipeline result
     */
    MediaPipelineResult runPipeline(MediaPipelineJob job);

    /**
     * Media pipeline job definition.
     */
    interface MediaPipelineJob extends ProviderJob {
        String pipelineDefinition();
        String inputUri();
        String outputUri();
        String pipelineType();
        String paramsJson();
    }

    /**
     * Media pipeline result.
     */
    record MediaPipelineResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}
}
