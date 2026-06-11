package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.TimelineRenderProvider;
import java.util.List;

/**
 * MLT render provider interface - POC / P1 / TimelineRenderProvider.
 * Timeline/NLE provider for multi-track editing.
 */
public interface MltRenderProviderInterface extends TimelineRenderProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "mlt",
                ProviderStatus.POC,
                "P1",
                ProviderType.TIMELINE,
                List.of(Capabilities.TIMELINE_RENDER, Capabilities.MULTI_TRACK,
                        Capabilities.TRANSITION, Capabilities.AUDIO_MIX),
                List.of(Capabilities.TIMELINE_RENDER, Capabilities.MULTI_TRACK,
                        Capabilities.TRANSITION, Capabilities.AUDIO_MIX),
                List.of(),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.RENDER_3D,
                        Capabilities.PACKAGE_HLS, Capabilities.PACKAGE_DASH),
                false,
                "server",
                "Timeline/NLE provider for multi-track editing, video stitching, transitions, audio mixing",
                List.of(
                        "Does NOT handle complex React subtitle templates",
                        "Does NOT handle 3D rendering",
                        "Output should be passed to FFmpeg for final normalization",
                        "If multi-track editing is needed, prioritize MLT over GStreamer"
                )
        );
    }
}
