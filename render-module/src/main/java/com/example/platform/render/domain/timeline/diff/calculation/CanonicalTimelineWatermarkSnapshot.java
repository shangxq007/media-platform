package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Watermark snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineWatermarkSnapshot(
        String watermarkId,
        String assetBindingId,
        String position,
        int opacityPercent,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineWatermarkSnapshot {
        if (watermarkId == null || watermarkId.isBlank())
            throw new IllegalArgumentException("watermarkId must not be blank");
        if (opacityPercent < 0 || opacityPercent > 100)
            throw new IllegalArgumentException("opacityPercent must be 0-100");
    }
}
