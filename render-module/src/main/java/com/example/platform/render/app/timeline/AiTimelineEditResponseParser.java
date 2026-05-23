package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelinePatchService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses AI output as full Internal Timeline JSON or a patch operation list.
 */
public final class AiTimelineEditResponseParser {

    private AiTimelineEditResponseParser() {}

    public static Parsed parse(String raw, TimelineSpecResolver resolver) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("AI edit output is empty");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = stripMarkdownFence(trimmed);
        }
        if (resolver.isInternalTimelineJson(trimmed)) {
            return Parsed.fullTimeline(trimmed);
        }
        try {
            JsonNode node = InternalTimelineJson.parse(trimmed);
            if (node.isObject()) {
                if (node.has("timelineJson") && node.get("timelineJson").isTextual()) {
                    String inner = node.get("timelineJson").asText();
                    if (resolver.isInternalTimelineJson(inner)) {
                        return Parsed.fullTimeline(inner.trim());
                    }
                }
                if (node.has("operations") && node.get("operations").isArray()) {
                    return Parsed.patchOps(parseOperations(node.get("operations")));
                }
            }
            if (node.isArray()) {
                return Parsed.patchOps(parseOperations(node));
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            throw new IllegalArgumentException(
                    "AI output is JSON but not Internal Timeline 1.0 nor patch operations[]");
        }
        throw new IllegalArgumentException("AI output is not timeline JSON or patch document");
    }

    private static String stripMarkdownFence(String s) {
        String t = s.replaceFirst("^```(?:json)?\\s*", "");
        int end = t.lastIndexOf("```");
        if (end > 0) {
            t = t.substring(0, end);
        }
        return t.trim();
    }

    private static List<TimelinePatchService.PatchOperation> parseOperations(JsonNode array) {
        List<TimelinePatchService.PatchOperation> ops = new ArrayList<>();
        for (JsonNode item : array) {
            String op = item.path("op").asText("");
            String path = item.path("path").asText("");
            JsonNode value = item.get("value");
            if (op.isBlank() || path.isBlank()) {
                continue;
            }
            ops.add(new TimelinePatchService.PatchOperation(op, path, value));
        }
        if (ops.isEmpty()) {
            throw new IllegalArgumentException("patch operations[] is empty");
        }
        return ops;
    }

    public sealed interface Parsed permits Parsed.FullTimeline, Parsed.PatchOps {

        static FullTimeline fullTimeline(String json) {
            return new FullTimeline(json);
        }

        static PatchOps patchOps(List<TimelinePatchService.PatchOperation> operations) {
            return new PatchOps(operations);
        }

        record FullTimeline(String timelineJson) implements Parsed {}

        record PatchOps(List<TimelinePatchService.PatchOperation> operations) implements Parsed {}
    }
}
