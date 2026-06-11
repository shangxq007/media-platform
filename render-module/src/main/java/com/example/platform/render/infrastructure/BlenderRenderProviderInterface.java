package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.ThreeDRenderProvider;
import java.util.List;

/**
 * Blender render provider interface - POC / P1 / ThreeDRenderProvider.
 * 3D render provider for 3D title sequences, logo reveals, product animations.
 */
public interface BlenderRenderProviderInterface extends ThreeDRenderProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "blender",
                ProviderStatus.POC,
                "P1",
                ProviderType.RENDER,
                List.of(Capabilities.RENDER_3D),
                List.of(Capabilities.RENDER_3D),
                List.of(),
                List.of(Capabilities.TRIM, Capabilities.TRANSCODE, Capabilities.TIMELINE_RENDER,
                        Capabilities.CAPTION_EFFECTS, Capabilities.PACKAGE_HLS, Capabilities.PACKAGE_DASH),
                false,
                "server",
                "3D render provider for 3D title sequences, logo reveals, product animations, 3D subtitles",
                List.of(
                        "NOT for ordinary subtitle videos or ordinary editing",
                        "NOT for 2D timeline editing",
                        "Output should be passed to FFmpeg for final normalization",
                        "Requires Blender binary and Python scripting"
                )
        );
    }
}
