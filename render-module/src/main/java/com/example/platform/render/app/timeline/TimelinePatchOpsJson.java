package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelinePatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** Serialize RFC6902 patch operations for {@code timeline_revision.patch_ops_json}. */
public final class TimelinePatchOpsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TimelinePatchOpsJson() {}

    public static String toJson(List<TimelinePatchService.PatchOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return null;
        }
        try {
            ArrayNode array = MAPPER.createArrayNode();
            for (TimelinePatchService.PatchOperation op : operations) {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("op", op.op());
                item.put("path", op.path());
                if (op.value() != null) {
                    item.set("value", op.value());
                }
                array.add(item);
            }
            return MAPPER.writeValueAsString(array);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<TimelinePatchService.PatchOperation> fromJson(String json) {
        List<TimelinePatchService.PatchOperation> ops = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return ops;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isArray()) {
                return ops;
            }
            for (JsonNode item : root) {
                String op = item.path("op").asText("");
                String path = item.path("path").asText("");
                JsonNode value = item.get("value");
                if (!op.isBlank() && !path.isBlank()) {
                    ops.add(new TimelinePatchService.PatchOperation(op, path, value));
                }
            }
        } catch (Exception ignored) {
            // return empty
        }
        return ops;
    }

    public static int countOps(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            return root.isArray() ? root.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
