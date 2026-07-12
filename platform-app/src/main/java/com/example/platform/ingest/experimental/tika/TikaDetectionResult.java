package com.example.platform.ingest.experimental.tika;

import java.util.List;

/**
 * Tika detection result. Internal only, not for user-facing exposure.
 */
public record TikaDetectionResult(
    String detectedContentType,
    String declaredContentType,
    String filename,
    String extension,
    boolean extensionMatchesDetectedType,
    boolean declaredMatchesDetectedType,
    String detector,
    List<String> warnings
) {
    public static TikaDetectionResult unknown(String filename, String declaredContentType) {
        return new TikaDetectionResult(
            "application/octet-stream", declaredContentType, filename,
            extractExtension(filename), false, false, "tika-detector",
            List.of("UNKNOWN_CONTENT_TYPE")
        );
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
