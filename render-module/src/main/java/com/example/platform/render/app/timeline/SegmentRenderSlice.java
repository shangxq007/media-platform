package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parsed {@code segmentRender} block from pipeline stage JSON.
 */
public record SegmentRenderSlice(
        String segmentId,
        int startFrame,
        int durationFrames,
        int fps) {

    public static SegmentRenderSlice fromJson(JsonNode root) {
        JsonNode segment = root.path("segmentRender");
        if (segment.isMissingNode()) {
            return null;
        }
        return new SegmentRenderSlice(
                segment.path("segmentId").asText("seg"),
                segment.path("startFrame").asInt(0),
                Math.max(1, segment.path("durationFrames").asInt(1)),
                Math.max(1, segment.path("fps").asInt(30)));
    }

    public double startSeconds() {
        return startFrame / (double) fps;
    }

    public double durationSeconds() {
        return durationFrames / (double) fps;
    }
}
