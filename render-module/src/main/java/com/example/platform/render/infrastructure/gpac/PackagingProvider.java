package com.example.platform.render.infrastructure.gpac;

import java.util.List;

/**
 * Port interface for media packaging providers.
 *
 * <p>Packaging providers take a mezzanine media file and produce streaming-ready
 * output (HLS manifests/segments, DASH manifests/segments, or CMAF chunks).</p>
 *
 * <p>Packaging is separate from rendering. A render job produces a mezzanine file,
 * and a packaging job converts it to streaming format.</p>
 */
public interface PackagingProvider {

    /**
     * Packages media according to the given request.
     *
     * @param request the packaging request
     * @return the packaging result
     * @throws IllegalStateException if packaging fails
     */
    PackagingResult packageMedia(PackagingRequest request);

    /**
     * Returns the list of formats this provider supports.
     *
     * @return list of format identifiers (e.g., "hls", "dash", "cmaf")
     */
    List<String> getSupportedFormats();

    /**
     * Validates that the packaging environment is correctly configured.
     *
     * @return true if the environment is valid
     */
    boolean validateEnvironment();
}
