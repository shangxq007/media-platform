package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Timeline render provider interface.
 * Suitable for: MltRenderProvider
 * Responsibilities: timeline_render, multi_track, transition, audio_mix
 */
public interface TimelineRenderProvider extends BaseProvider {

    /**
     * Render a timeline job.
     * @param job the timeline render job
     * @return the render result
     */
    TimelineRenderResult renderTimeline(TimelineRenderJob job);

    /**
     * Timeline render job definition.
     */
    interface TimelineRenderJob extends ProviderJob {
        String timelineJson();
        String outputUri();
        String format();
        String resolution();
        int frameRate();
        boolean hasMultiTrack();
        boolean hasTransitions();
        boolean hasAudioMix();
    }

    /**
     * Timeline render result.
     */
    record TimelineRenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}
}
