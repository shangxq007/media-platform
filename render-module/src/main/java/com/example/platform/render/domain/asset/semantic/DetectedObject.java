package com.example.platform.render.domain.asset.semantic;

/**
 * A detected object with label and time range.
 */
public record DetectedObject(
        String label,
        double confidence,
        long startTimeMs,
        long endTimeMs) {
}
