package com.example.platform.render.infrastructure.gpac;

import java.util.List;
import java.util.Map;

/**
 * Request to package media into a streaming format.
 *
 * @param inputUri     URI of the input media file (e.g., mezzanine MP4)
 * @param outputBase   base URI for output files (manifests and segments)
 * @param format       packaging format ("hls", "dash", or "cmaf")
 * @param segmentDuration segment duration in seconds
 * @param profiles     encoding profiles for adaptive bitrate
 * @param extraParams  additional MP4Box-specific parameters
 */
public record PackagingRequest(
        String inputUri,
        String outputBase,
        String format,
        int segmentDuration,
        List<String> profiles,
        Map<String, String> extraParams) {

    /**
     * Creates an HLS packaging request.
     */
    public static PackagingRequest hls(String inputUri, String outputBase, int segmentDuration) {
        return new PackagingRequest(inputUri, outputBase, "hls",
                segmentDuration, List.of(), Map.of());
    }

    /**
     * Creates a DASH packaging request.
     */
    public static PackagingRequest dash(String inputUri, String outputBase, int segmentDuration) {
        return new PackagingRequest(inputUri, outputBase, "dash",
                segmentDuration, List.of(), Map.of());
    }
}
