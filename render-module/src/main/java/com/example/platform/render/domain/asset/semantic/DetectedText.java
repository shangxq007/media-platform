package com.example.platform.render.domain.asset.semantic;

/**
 * OCR-detected text at a specific time in the asset.
 */
public record DetectedText(
        String text,
        double confidence,
        long startTimeMs,
        long endTimeMs) {
}
