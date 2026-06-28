package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Artifact DAG impact analysis for a timeline diff.
 * Internal domain model. Safe identifiers only, no storage object keys.
 */
public record ArtifactDAGImpact(
        String impactId,
        List<String> affectedNodeKeys,
        List<String> reusableNodeKeys,
        Map<String, String> safeMetadata) {}
