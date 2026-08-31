package com.example.platform.render.app.operation;

import java.util.List;

/**
 * H7 render-application result for an authorized media-clip operation.
 *
 * <p>The operation transaction model is deliberately translated here so web
 * adapters never depend on operation-module result types.
 */
public record AddMediaClipResult(
        String status,
        String planDigest,
        String baseRevisionId,
        String newRevisionId,
        String newTimelineContentHash,
        String parentRevisionId,
        TimelineRevisionRenderHandoff renderHandoff,
        List<String> semanticDiff) {

    public AddMediaClipResult {
        semanticDiff = List.copyOf(semanticDiff);
    }

    public record TimelineRevisionRenderHandoff(
            String projectId,
            String timelineRevisionId,
            String timelineContentHash) {
    }
}
