package com.example.platform.timeline.app;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.timeline.app.TimelineCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Applies JSON Patch-style operations to Internal Timeline Schema 1.0 JSON (AI / MCP safe edits).
 * GCR-1 CORRECTION V1: timeline-owned; validation via TimelineCanonicalizer (format authority);
 * TimelineSpec projection conversion removed (render-side consumers convert via InternalTimelineAdapter).
 */
@Service
public class TimelinePatchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineCanonicalizer timelineCanonicalizer;

    public TimelinePatchService(TimelineCanonicalizer timelineCanonicalizer) {
        this.timelineCanonicalizer = timelineCanonicalizer;
    }

    public PatchResult applyPatch(String timelineJson, List<PatchOperation> operations) {
        try {
            JsonNode root = MAPPER.readTree(timelineJson);
            if (!root.isObject()) {
                return PatchResult.failed(List.of("Timeline root must be an object"));
            }
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return PatchResult.failed(List.of(
                        "Internal Timeline 1.0 required (schemaVersion 1.0 + composition). "
                                + "Use import_otio / import_edl to convert exchange formats."));
            }
            ObjectNode doc = (ObjectNode) root;
            List<String> applied = new ArrayList<>();
            boolean revisionTouched = false;

            for (PatchOperation op : operations) {
                if (op.path() != null && (op.path().equals("/revision")
                        || op.path().startsWith("/revision/"))) {
                    revisionTouched = true;
                }
                applyOperation(doc, op);
                applied.add(op.op() + " " + op.path());
            }

            if (!revisionTouched) {
                int rev = doc.path("revision").asInt(0);
                doc.put("revision", rev + 1);
            }

            String patched = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
            try {
                TimelineCanonicalizer.CanonicalizeResult canonical =
                        timelineCanonicalizer.canonicalize(patched);
                return PatchResult.success(canonical.timelineJson(), applied);
            } catch (IllegalArgumentException e) {
                return PatchResult.failed(List.of(e.getMessage()));
            }
        } catch (Exception e) {
            return PatchResult.failed(List.of("Patch failed: " + e.getMessage()));
        }
    }

    private void applyOperation(ObjectNode doc, PatchOperation op) {
        String path = op.path();
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with /: " + path);
        }
        String[] segments = path.substring(1).split("/");
        JsonNode parent = doc;
        for (int i = 0; i < segments.length - 1; i++) {
            parent = navigate(parent, segments[i], true);
        }
        String leaf = segments[segments.length - 1];
        switch (op.op().toLowerCase()) {
            case "replace", "add" -> setAt(parent, leaf, op.value());
            case "remove" -> removeAt(parent, leaf);
            default -> throw new IllegalArgumentException("Unsupported op: " + op.op());
        }
    }

    private JsonNode navigate(JsonNode node, String segment, boolean create) {
        if (segment.matches("\\d+")) {
            int idx = Integer.parseInt(segment);
            if (!node.isArray()) {
                throw new IllegalArgumentException("Expected array at segment " + segment);
            }
            ArrayNode arr = (ArrayNode) node;
            while (create && idx >= arr.size()) {
                arr.addObject();
            }
            return arr.get(idx);
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Expected object at segment " + segment);
        }
        ObjectNode obj = (ObjectNode) node;
        if (!obj.has(segment) && create) {
            obj.set(segment, MAPPER.createObjectNode());
        }
        return obj.get(segment);
    }

    private void setAt(JsonNode parent, String leaf, JsonNode value) {
        if (leaf.matches("\\d+")) {
            ((ArrayNode) parent).set(Integer.parseInt(leaf), value);
        } else {
            ((ObjectNode) parent).set(leaf, value);
        }
    }

    private void removeAt(JsonNode parent, String leaf) {
        if (leaf.matches("\\d+")) {
            ((ArrayNode) parent).remove(Integer.parseInt(leaf));
        } else {
            ((ObjectNode) parent).remove(leaf);
        }
    }

    public record PatchOperation(String op, String path, JsonNode value) {}

    public record PatchResult(
            boolean success,
            String timelineJson,
            List<String> appliedOps,
            List<String> errors,
            List<String> warnings) {

        public static PatchResult success(String json, List<String> applied) {
            return new PatchResult(true, json, applied, List.of(), List.of());
        }

        public static PatchResult failed(List<String> errors) {
            return new PatchResult(false, null, List.of(), errors, List.of());
        }
    }
}
