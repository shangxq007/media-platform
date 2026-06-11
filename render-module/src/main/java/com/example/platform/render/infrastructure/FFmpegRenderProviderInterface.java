package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.MediaProcessingProvider;
import java.util.List;

/**
 * FFmpeg render provider - Production / P0 / MediaProcessingProvider.
 * Core media processing and output normalization.
 */
public interface FFmpegRenderProviderInterface extends MediaProcessingProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "ffmpeg",
                ProviderStatus.PRODUCTION,
                "P0",
                ProviderType.RENDER,
                List.of(Capabilities.TRIM, Capabilities.TRANSCODE, Capabilities.MUX,
                        Capabilities.DEMUX, Capabilities.EXTRACT_AUDIO, Capabilities.THUMBNAIL,
                        Capabilities.CAPTION_BURN_IN, Capabilities.OUTPUT_NORMALIZE),
                List.of(Capabilities.TRIM, Capabilities.TRANSCODE, Capabilities.MUX,
                        Capabilities.DEMUX, Capabilities.EXTRACT_AUDIO, Capabilities.THUMBNAIL,
                        Capabilities.OUTPUT_NORMALIZE),
                List.of(Capabilities.CAPTION_BURN_IN),
                List.of(Capabilities.CAPTION_EFFECTS, Capabilities.TEMPLATE_RENDER,
                        Capabilities.TIMELINE_RENDER, Capabilities.RENDER_3D, Capabilities.VFX_COMPOSITE),
                true,
                "server",
                "Core media processing and output normalization",
                List.of("Do not add complex subtitle templates, 3D, NLE timeline capabilities")
        );
    }
}
