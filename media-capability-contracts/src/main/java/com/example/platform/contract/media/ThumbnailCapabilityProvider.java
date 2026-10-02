package com.example.platform.contract.media;

import com.example.platform.contract.media.FrameExtractManifest;
import com.example.platform.contract.media.FrameExtractResult;
import java.util.function.BooleanSupplier;

/**
 * Stable typed media.thumbnail invocation contract.
 *
 * <p>Provider identity model is the same as {@code CoverImageCapabilityProvider}: a
 * capability-independent provider <em>family</em> ({@code providerId}) plus one implementation
 * identity ({@code providerImplementationId}), and the provider declares the capabilities it
 * serves as a <em>list</em>. The capability/&lt;-&gt;provider relation is therefore explicitly
 * N:M: one provider may declare several capabilities, and one capability may be served by several
 * providers.
 */
public interface ThumbnailCapabilityProvider {
    FrameExtractManifest manifest();
    FrameExtractResult extract(String capabilityId, ThumbnailContracts.Request request, byte[] input,
            BooleanSupplier cancelled);

    /** True when this provider declares the given capability. */
    default boolean supports(String capabilityId) {
        return manifest().supports(capabilityId);
    }

}
