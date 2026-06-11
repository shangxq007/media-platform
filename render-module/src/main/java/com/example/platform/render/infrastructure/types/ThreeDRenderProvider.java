package com.example.platform.render.infrastructure.types;

import com.example.platform.render.infrastructure.BaseProvider;
import com.example.platform.render.infrastructure.ProviderJob;

/**
 * 3D render provider interface.
 * Suitable for: BlenderRenderProvider
 * Responsibilities: 3d_render, logo_reveal, product_animation, 3d_text, visual_asset_generation
 */
public interface ThreeDRenderProvider extends BaseProvider {

    /**
     * Render a 3D job.
     * @param job the 3D render job
     * @return the render result
     */
    ThreeDRenderResult render3D(ThreeDRenderJob job);

    /**
     * 3D render job definition.
     */
    interface ThreeDRenderJob extends ProviderJob {
        String blendFileUri();
        String pythonScriptUri();
        String paramsJson();
        String outputUri();
        String outputFormat();
        boolean transparentBackground();
    }

    /**
     * 3D render result.
     */
    record ThreeDRenderResult(
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success
    ) {}
}
