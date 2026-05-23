package com.example.platform.render.app;

import com.example.platform.render.infrastructure.ColorProbeMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Merges probe-derived color/HDR metadata into Internal Timeline JSON.
 */
@Service
public class TimelineColorMetadataService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String mergeProbeMetadata(String timelineJson, ColorProbeMetadata color) {
        if (timelineJson == null || timelineJson.isBlank() || color == null) {
            return timelineJson;
        }
        try {
            JsonNode root = MAPPER.readTree(timelineJson);
            if (!root.isObject()) {
                return timelineJson;
            }
            ObjectNode obj = (ObjectNode) root;
            ObjectNode metadata = obj.has("metadata") && obj.get("metadata").isObject()
                    ? (ObjectNode) obj.get("metadata")
                    : obj.putObject("metadata");
            for (Map.Entry<String, String> e : color.toTimelineMetadata().entrySet()) {
                metadata.put(e.getKey(), e.getValue());
            }
            if (color.hdr() && obj.has("project") && obj.get("project").isObject()) {
                ObjectNode project = (ObjectNode) obj.get("project");
                if (!project.has("colorSpace") || project.get("colorSpace").asText("").isBlank()) {
                    project.put("colorSpace", "HDR");
                }
            }
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return timelineJson;
        }
    }
}
