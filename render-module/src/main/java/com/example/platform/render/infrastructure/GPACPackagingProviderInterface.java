package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.types.PackagingProvider;
import java.util.List;

/**
 * GPAC packaging provider interface - POC / P1 / PackagingProvider.
 * DASH/HLS/CMAF packaging provider for streaming delivery.
 */
public interface GPACPackagingProviderInterface extends PackagingProvider {

    @Override
    default ProviderMetadata getMetadata() {
        return new ProviderMetadata(
                "gpac",
                ProviderStatus.POC,
                "P1",
                ProviderType.PACKAGING,
                List.of(Capabilities.PACKAGE_HLS, Capabilities.PACKAGE_DASH,
                        Capabilities.PACKAGE_CMAF),
                List.of(Capabilities.PACKAGE_HLS, Capabilities.PACKAGE_DASH,
                        Capabilities.PACKAGE_CMAF),
                List.of(),
                List.of(Capabilities.TRIM, Capabilities.TRANSCODE, Capabilities.RENDER_3D,
                        Capabilities.TIMELINE_RENDER, Capabilities.CAPTION_EFFECTS),
                false,
                "server",
                "DASH/HLS/CMAF packaging provider for streaming delivery",
                List.of(
                        "Not a general render provider - only for packaging/streaming delivery",
                        "Does not enter main render scheduling unless HLS/DASH packaging is required",
                        "If short-term no HLS/DASH demand, can remain POC without entering main rendering pipeline"
                )
        );
    }
}
