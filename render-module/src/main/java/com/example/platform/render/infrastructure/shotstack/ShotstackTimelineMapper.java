package com.example.platform.render.infrastructure.shotstack;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps platform timeline JSON to a minimal Shotstack Edit API payload.
 */
@Component
public class ShotstackTimelineMapper {

    private final TimelineScriptParser timelineScriptParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShotstackTimelineMapper(TimelineScriptParser timelineScriptParser) {
        this.timelineScriptParser = timelineScriptParser;
    }

    public Optional<ObjectNode> toEditPayload(String timelineScript, Map<String, Object> effectParams) {
        Optional<TimelineSpec> spec = timelineScriptParser.parse(timelineScript);
        if (spec.isEmpty()) {
            return Optional.empty();
        }

        String src = null;
        double length = 10.0;
        for (TimelineTrack track : spec.get().tracks()) {
            if (track.clips() == null) {
                continue;
            }
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() != null && clip.assetRef().storageUri() != null) {
                    src = clip.assetRef().storageUri();
                    if (clip.clipDuration() > 0) {
                        length = clip.clipDuration();
                    }
                    break;
                }
            }
            if (src != null) {
                break;
            }
        }
        if (src == null) {
            return Optional.empty();
        }

        String resolution = effectParams != null
                ? String.valueOf(effectParams.getOrDefault("resolution", "hd"))
                : "hd";

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode timeline = root.putObject("timeline");
        ArrayNode tracks = timeline.putArray("tracks");
        ObjectNode videoTrack = tracks.addObject();
        ArrayNode clips = videoTrack.putArray("clips");
        ObjectNode clip = clips.addObject();
        clip.put("asset", objectMapper.createObjectNode().put("type", "video").put("src", src));
        clip.put("start", 0);
        clip.put("length", length);

        ObjectNode output = root.putObject("output");
        output.put("format", "mp4");
        output.put("resolution", resolution);

        return Optional.of(root);
    }
}
