package com.example.platform.render.domain.asset.semantic;

/**
 * A detected scene with a time range and label.
 */
public record Scene(
        String sceneId,
        String label,
        long startTimeMs,
        long endTimeMs,
        double confidence) {

    public static Scene of(String label, long startTimeMs, long endTimeMs, double confidence) {
        return new Scene("sc_" + System.currentTimeMillis() + "_" + label.hashCode(),
                label, startTimeMs, endTimeMs, confidence);
    }
}
