package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Overlay provider interface.
 * Suitable for: LibassOverlayProvider
 * Responsibilities: subtitle_overlay, ass_ssa_render, caption_burn_in
 */
public interface OverlayProvider extends BaseProvider {

    /**
     * Apply an overlay job.
     * @param job the overlay job
     * @return the overlay result
     */
    OverlayResult applyOverlay(OverlayJob job);

    /**
     * Overlay job definition.
     */
    interface OverlayJob extends ProviderJob {
        String inputUri();
        String outputUri();
        String subtitleUri();
        String subtitleFormat();
        String styleJson();
    }

    /**
     * Overlay result.
     */
    record OverlayResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success,
            boolean wasSkipped,
            String errorMessage
    ) {}
}
