package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Product lineage impact analysis for a timeline diff.
 * Internal domain model. Safe product IDs only.
 */
public record ProductLineageImpact(
        String impactId,
        List<String> affectedProductIds,
        List<String> derivedProductIds,
        Map<String, String> safeMetadata) {}
