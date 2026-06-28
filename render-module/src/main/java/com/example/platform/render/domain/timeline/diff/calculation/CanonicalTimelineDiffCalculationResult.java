package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.render.domain.timeline.diff.TimelineDiff;
import java.util.List;
import java.util.Map;

/**
 * Result of canonical timeline diff calculation.
 * Internal domain model.
 */
public record CanonicalTimelineDiffCalculationResult(
        TimelineDiff diff,
        boolean successful,
        List<String> warnings,
        Map<String, String> safeMetadata) {

    public static CanonicalTimelineDiffCalculationResult success(TimelineDiff diff) {
        return new CanonicalTimelineDiffCalculationResult(diff, true, List.of(), Map.of());
    }

    public static CanonicalTimelineDiffCalculationResult failure(String message) {
        return new CanonicalTimelineDiffCalculationResult(null, false, List.of(message), Map.of());
    }
}
