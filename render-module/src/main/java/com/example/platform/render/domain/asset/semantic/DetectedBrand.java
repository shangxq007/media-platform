package com.example.platform.render.domain.asset.semantic;

/**
 * A detected brand logo with time range.
 */
public record DetectedBrand(
        String brandName,
        double confidence,
        long startTimeMs,
        long endTimeMs) {
}
