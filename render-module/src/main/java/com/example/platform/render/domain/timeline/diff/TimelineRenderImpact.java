package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Render impact analysis for a timeline diff.
 * Internal domain model. Does not compute impact — stores analysis result.
 */
public record TimelineRenderImpact(
        TimelineRenderImpactLevel level,
        List<String> affectedTimelinePaths,
        List<String> affectedArtifactKeys,
        Map<String, String> safeMetadata) {

    public static TimelineRenderImpact metadataOnly() {
        return new TimelineRenderImpact(
                TimelineRenderImpactLevel.METADATA_ONLY, List.of(), List.of(), Map.of());
    }

    public static TimelineRenderImpact fullRerender() {
        return new TimelineRenderImpact(
                TimelineRenderImpactLevel.FULL_RERENDER, List.of(), List.of(), Map.of());
    }
}
