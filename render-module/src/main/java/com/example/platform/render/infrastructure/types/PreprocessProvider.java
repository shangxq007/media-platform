package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Preprocess provider interface.
 * Suitable for: VapourSynthPreprocessProvider
 * Responsibilities: preprocess, denoise, deinterlace, fps_convert, video_enhance
 */
public interface PreprocessProvider extends BaseProvider {

    /**
     * Preprocess a video job.
     * @param job the preprocess job
     * @return the preprocess result
     */
    PreprocessResult preprocess(PreprocessJob job);

    /**
     * Preprocess job definition.
     */
    interface PreprocessJob extends ProviderJob {
        String inputUri();
        String outputUri();
        String preprocessType();
        String paramsJson();
    }

    /**
     * Preprocess result.
     */
    record PreprocessResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}
}
