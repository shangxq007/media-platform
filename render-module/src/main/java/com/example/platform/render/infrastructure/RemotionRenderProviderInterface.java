package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.CompositionRenderProvider;
import java.util.List;

/**
 * Remotion render provider interface - POC / P1 / CompositionRenderProvider.
 * Subtitle effects and template-based video composition.
 */
public interface RemotionRenderProviderInterface extends CompositionRenderProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "remotion",
                ProviderStatus.POC,
                "P1",
                ProviderType.RENDER,
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS,
                        Capabilities.TEMPLATE_RENDER, Capabilities.PREVIEW),
                List.of(Capabilities.CAPTION_BURN_IN, Capabilities.CAPTION_EFFECTS,
                        Capabilities.TEMPLATE_RENDER),
                List.of(Capabilities.PREVIEW),
                List.of(Capabilities.TRIM, Capabilities.TRANSCODE, Capabilities.EXTRACT_AUDIO,
                        Capabilities.TIMELINE_RENDER, Capabilities.RENDER_3D,
                        Capabilities.PACKAGE_HLS, Capabilities.PACKAGE_DASH),
                false,
                "server",
                "Subtitle effects and template-based video composition",
                List.of(
                        "Does NOT handle video trim, transcode, audio extraction, format repair",
                        "Fonts must use unified font asset management, no system font dependency",
                        "Subtitle line breaks and timeline must be provided by upstream RenderJob",
                        "Output should be passed to FFmpeg for final normalization"
                )
        );
    }
}
