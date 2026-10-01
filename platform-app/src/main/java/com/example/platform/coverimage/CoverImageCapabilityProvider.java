package com.example.platform.coverimage;

import com.example.platform.frameextract.FrameExtractManifest;
import com.example.platform.frameextract.FrameExtractResult;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * Stable typed media.cover-image invocation contract. Provider-specific mechanics (FFmpeg, sandbox
 * toolchain, argument shaping) stay behind this boundary.
 *
 * <p>Model: a provider is identified by a <em>provider family</em> plus one implementation identity,
 * both independent of any capability, and it <em>declares the capabilities it serves</em> as a list
 * (mirrors the platform provider contract shape: provider identity + capability declarations). One
 * provider may therefore serve several capabilities, and one capability may be served by several
 * providers.
 */
public interface CoverImageCapabilityProvider {

    FrameExtractManifest manifest();

    /**
     * Renders one cover image from an already digest-verified subject artifact.
     *
     * @param capabilityId capability being executed; the provider must declare it. The provider is
     *                     told which capability it is executing so one implementation can serve
     *                     several capabilities with distinct rendering profiles.
     * @param request   canonical cover request
     * @param inputPath path of the digest-verified subject artifact bytes
     * @param cancelled cancellation probe owned by the caller
     */
    FrameExtractResult render(String capabilityId, CoverImageContracts.Request request, Path inputPath,
                              BooleanSupplier cancelled);

    /** True when this provider declares the given capability. */
    default boolean supports(String capabilityId) {
        return manifest().supports(capabilityId);
    }

}
