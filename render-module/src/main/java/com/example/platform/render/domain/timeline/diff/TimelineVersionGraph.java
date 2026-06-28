package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Version history graph for timeline versions.
 * Internal domain model. No repository, no traversal algorithm.
 */
public record TimelineVersionGraph(
        String graphId,
        List<TimelineCommit> commits,
        Map<String, String> safeMetadata) {

    public TimelineVersionGraph {
        if (graphId == null || graphId.isBlank())
            throw new IllegalArgumentException("Graph ID must not be blank");
    }
}
