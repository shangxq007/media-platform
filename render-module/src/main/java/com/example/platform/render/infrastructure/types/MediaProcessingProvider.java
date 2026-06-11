package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Media processing provider interface.
 * Suitable for: FFmpegRenderProvider
 * Responsibilities: trim, transcode, mux, demux, extract_audio, thumbnail, output_normalize
 */
public interface MediaProcessingProvider extends BaseProvider {

    /**
     * Process a media job.
     * @param job the media processing job
     * @return the processing result
     */
    MediaProcessingResult process(MediaProcessingJob job);

    /**
     * Media processing job definition.
     */
    interface MediaProcessingJob extends ProviderJob {
        String inputUri();
        String outputUri();
        String format();
        String codec();
        String resolution();
        int frameRate();
    }

    /**
     * Media processing result.
     */
    record MediaProcessingResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}
}
