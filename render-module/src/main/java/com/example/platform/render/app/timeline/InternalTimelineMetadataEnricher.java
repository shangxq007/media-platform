package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelinePlatformMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Merges platform AI / edit-session fields into Internal Timeline 1.0 JSON.
 */
@Component
public class InternalTimelineMetadataEnricher {

    public String enrichJson(String timelineJson, AiTimelineEditContext context, String source) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (!root.isObject()) {
                return timelineJson;
            }
            ObjectNode doc = (ObjectNode) root;
            ObjectNode metadata = doc.has("metadata") && doc.get("metadata").isObject()
                    ? (ObjectNode) doc.get("metadata")
                    : InternalTimelineJson.mapper().createObjectNode();
            applyContext(metadata, context, source);
            doc.set("metadata", metadata);
            int rev = doc.path("revision").asInt(0);
            doc.put("revision", rev + 1);
            return InternalTimelineJson.write(doc);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to enrich timeline metadata: " + e.getMessage(), e);
        }
    }

    public Map<String, String> toMetadataMap(AiTimelineEditContext context, String source) {
        Map<String, String> meta = new LinkedHashMap<>();
        if (context.tenantId() != null) {
            meta.put("tenantId", context.tenantId());
        }
        if (context.projectId() != null) {
            meta.put("projectId", context.projectId());
        }
        if (source != null) {
            meta.put(TimelinePlatformMetadata.AI_SOURCE, source);
        }
        putIfPresent(meta, TimelinePlatformMetadata.AI_EDIT_SESSION_ID, context.editSessionId());
        putIfPresent(meta, TimelinePlatformMetadata.AI_PARENT_JOB_ID, context.parentJobId());
        putIfPresent(meta, TimelinePlatformMetadata.AI_INTENT, context.intent());
        putIfPresent(meta, TimelinePlatformMetadata.AI_CONVERSATION_ID, context.conversationId());
        putIfPresent(meta, TimelinePlatformMetadata.AI_LAST_INSTRUCTION, context.lastInstruction());
        putIfPresent(meta, TimelinePlatformMetadata.AI_LAST_MODEL, context.lastModel());
        return meta;
    }

    private void applyContext(ObjectNode metadata, AiTimelineEditContext context, String source) {
        toMetadataMap(context, source).forEach(metadata::put);
    }

    private static void putIfPresent(Map<String, String> meta, String key, String value) {
        if (value != null && !value.isBlank()) {
            meta.put(key, value);
        }
    }
}
