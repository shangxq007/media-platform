package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.OverlayProvider;
import java.util.List;

/**
 * Libass overlay provider interface - POC / P1 / OverlayProvider.
 * ASS/SSA subtitle overlay provider for standard subtitle rendering.
 */
public interface LibassOverlayProviderInterface extends OverlayProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "libass",
                ProviderStatus.POC,
                "P1",
                ProviderType.OVERLAY,
                List.of(Capabilities.SUBTITLE_OVERLAY, Capabilities.ASS_SSA_RENDER,
                        Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.SUBTITLE_OVERLAY, Capabilities.ASS_SSA_RENDER,
                        Capabilities.CAPTION_BURN_IN),
                List.of(),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER,
                        Capabilities.RENDER_3D, Capabilities.TIMELINE_RENDER),
                false,
                "server",
                "ASS/SSA subtitle overlay provider for standard subtitle rendering and burn-in",
                List.of(
                        "Does NOT handle complex subtitle templates or React-based animations",
                        "For complex dynamic subtitles, use RemotionRenderProvider instead",
                        "Output should be passed to FFmpeg for final normalization"
                )
        );
    }
}
