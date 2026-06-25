package com.example.platform.render.domain.asset.semantic;

/**
 * A detected person with optional identity and time range.
 */
public record DetectedPerson(
        String name,
        double confidence,
        long startTimeMs,
        long endTimeMs) {
}
