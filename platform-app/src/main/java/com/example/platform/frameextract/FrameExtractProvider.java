package com.example.platform.frameextract;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * Capability-neutral frame-extract provider contract.
 *
 * <p>This is the single 1:N boundary the cover and thumbnail capabilities share: one provider family
 * plus one implementation identity, with the capability list it serves declared as data. The caller
 * passes the executing {@code capabilityId}; the provider selects the matching
 * {@link FrameExtractCapabilityProfile} and fails closed for a capability it does not declare. One
 * provider may therefore serve several capabilities, and one capability may be served by several
 * providers.
 *
 * <p>No provider/backend identity is capability-scoped: the manifest carries the capability-neutral
 * family ({@code platform.ffmpeg}) and implementation ({@code ffmpeg.cpu.frame-extract.v1}) exactly
 * once, with the capabilities as a list.
 */
public interface FrameExtractProvider {

    /** Capability-neutral provider declaration: family + implementation identity + capability list. */
    FrameExtractManifest manifest();

    /** True when this provider declares the given capability. */
    default boolean supports(String capabilityId) {
        return manifest().supports(capabilityId);
    }

    /**
     * Extracts exactly one frame for the given capability into {@code workDirectory}.
     *
     * @param capabilityId capability being executed; the provider must declare it and must have a
     *                     profile for it
     * @param input        digest-verified subject bytes on disk
     * @param workDirectory writable scratch directory for this invocation; the provider owns what it
     *                      writes there
     * @param imageFormat  requested raster encoding ({@code png} or {@code jpeg})
     * @param width        requested width, or {@code null} for the native size
     * @param quality      requested quality (1..100), or {@code null} for the documented default
     * @param timestampSeconds source instant, already decoded from the transport value
     * @param cancelled    cancellation probe owned by the caller
     */
    FrameExtractResult render(
            String capabilityId,
            Path input,
            Path workDirectory,
            String imageFormat,
            Integer width,
            Integer quality,
            double timestampSeconds,
            BooleanSupplier cancelled);

}
