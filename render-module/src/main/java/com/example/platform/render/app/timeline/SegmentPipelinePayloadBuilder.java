package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds timeline JSON payloads for segment render / stitch pipeline stages.
 */
public final class SegmentPipelinePayloadBuilder {

    private static final ObjectMapper MAPPER = InternalTimelineJson.mapper();

    private SegmentPipelinePayloadBuilder() {}

    public static String segmentRenderPayload(TimelineSpec timeline,
                                              String segmentId,
                                              int startFrame,
                                              int durationFrames,
                                              int fps,
                                              String upstreamInputUri) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", timeline.id() + "-" + segmentId);
        root.set("tracks", MAPPER.valueToTree(timeline.tracks()));
        if (timeline.outputSpec() != null) {
            root.set("outputSpec", MAPPER.valueToTree(timeline.outputSpec()));
        }
        ObjectNode segment = root.putObject("segmentRender");
        segment.put("segmentId", segmentId);
        segment.put("startFrame", startFrame);
        segment.put("durationFrames", durationFrames);
        segment.put("fps", fps);
        if (upstreamInputUri != null) {
            segment.put("upstreamInputUri", upstreamInputUri);
        }
        return MAPPER.writeValueAsString(root);
    }

    public static String segmentStitchPayload(TimelineSpec timeline,
                                              List<String> orderedSegmentIds,
                                              Map<String, String> segmentArtifacts,
                                              int fps) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", timeline.id() + "-stitch");
        if (timeline.outputSpec() != null) {
            root.set("outputSpec", MAPPER.valueToTree(timeline.outputSpec()));
        }
        ObjectNode stitch = root.putObject("segmentStitch");
        stitch.put("fps", fps);
        var segments = stitch.putArray("segments");
        for (String segId : orderedSegmentIds) {
            ObjectNode entry = segments.addObject();
            entry.put("id", segId);
            entry.put("uri", segmentArtifacts.getOrDefault(segId, ""));
        }
        return MAPPER.writeValueAsString(root);
    }

    public static Map<String, String> orderedSegmentArtifacts(List<String> orderedIds,
                                                              Map<String, String> artifacts) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String id : orderedIds) {
            if (artifacts.containsKey(id)) {
                ordered.put(id, artifacts.get(id));
            }
        }
        return ordered;
    }
}
