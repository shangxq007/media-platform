package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * Composition render provider interface.
 * Suitable for: RemotionRenderProvider
 * Responsibilities: caption_burn_in, caption_effects, template_render, preview
 */
public interface CompositionRenderProvider extends BaseProvider {

    /**
     * Render a composition job.
     * @param job the composition render job
     * @return the render result
     */
    CompositionRenderResult renderComposition(CompositionRenderJob job);

    /**
     * Render a preview (optional, may not be enabled in all providers).
     * @param job the composition render job
     * @return the preview result
     */
    default PreviewResult renderPreview(CompositionRenderJob job) {
        return null;
    }

    /**
     * Composition render job definition.
     */
    interface CompositionRenderJob extends ProviderJob {
        String compositionId();
        String templateId();
        String propsJson();
        String outputUri();
        String fontFamily();
        String fontAssetUri();
        String captionsJson();
    }

    /**
     * Composition render result.
     */
    record CompositionRenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}

    /**
     * Preview result.
     */
    record PreviewResult(
            String previewUrl,
            String format,
            String resolution,
            boolean success
    ) {}
}
