package com.example.platform.render.infrastructure.gpac;

import java.util.List;

/**
 * Result of a packaging operation.
 *
 * @param manifestUri   URI of the generated manifest file
 * @param segmentUris   URIs of generated segment files
 * @param format        the packaging format used
 * @param duration      total duration in seconds
 * @param success       whether packaging succeeded
 * @param errorMessage  error message if packaging failed
 */
public record PackagingResult(
        String manifestUri,
        List<String> segmentUris,
        String format,
        long duration,
        boolean success,
        String errorMessage) {

    /**
     * Creates a successful result.
     */
    public static PackagingResult success(String manifestUri, List<String> segmentUris,
            String format, long duration) {
        return new PackagingResult(manifestUri, segmentUris, format, duration, true, null);
    }

    /**
     * Creates a failed result.
     */
    public static PackagingResult failed(String errorMessage) {
        return new PackagingResult(null, List.of(), null, 0, false, errorMessage);
    }
}
