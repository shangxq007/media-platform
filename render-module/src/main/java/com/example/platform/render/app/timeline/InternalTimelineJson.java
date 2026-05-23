package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * JSON utilities for Internal Timeline Schema 1.0.
 */
public final class InternalTimelineJson {

    public static final String META_TEMPLATES = "platform.templates";
    public static final String META_STYLES = "platform.styles";
    public static final String META_RENDER_GRAPH_LAYERS = "platform.renderGraphLayers";

    public static final String SCHEMA_V1 = "1.0";

    public static final Set<String> VOLATILE_FIELDS = Set.of(
            "lastProbeAt",
            "lastModifiedAt",
            "probedAt",
            "updatedAt",
            "etag",
            "requestId");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InternalTimelineJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode parse(String json) throws java.io.IOException {
        return MAPPER.readTree(json);
    }

    public static String write(JsonNode node) throws java.io.IOException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    /** True when document is Internal Timeline 1.0 (schemaVersion 1.x or canonical composition block). */
    public static boolean isInternalTimeline(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        String version = root.path("schemaVersion").asText("");
        if (version.startsWith("1")) {
            return true;
        }
        return root.has("composition") && root.get("composition").isObject();
    }

    public static String timelineId(JsonNode root) {
        return root.path("id").asText("timeline");
    }

    public static int revision(JsonNode root) {
        return root.path("revision").asInt(0);
    }

    public static String schemaVersion(JsonNode root) {
        if (isInternalTimeline(root)) {
            return SCHEMA_V1;
        }
        return root.path("schemaVersion").asText("");
    }

    public static JsonNode deepCanonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (VOLATILE_FIELDS.contains(e.getKey())) {
                    continue;
                }
                sorted.put(e.getKey(), deepCanonicalize(e.getValue()));
            }
            sorted.forEach(out::set);
            return out;
        }
        if (node.isArray()) {
            ArrayNode arr = MAPPER.createArrayNode();
            List<JsonNode> items = new java.util.ArrayList<>();
            node.forEach(items::add);
            if (items.stream().allMatch(n -> n.isObject() && n.has("id"))) {
                items.sort((a, b) -> a.get("id").asText("").compareTo(b.get("id").asText("")));
            }
            for (JsonNode item : items) {
                arr.add(deepCanonicalize(item));
            }
            return arr;
        }
        return node;
    }

    public static void sortKeyedEntityMaps(ObjectNode root) {
        sortKeyedMap(root, "assetRegistry", "assets");
        sortKeyedMap(root, "styles", null);
        sortKeyedMap(root, "templates", null);
    }

    private static void sortKeyedMap(ObjectNode root, String parentField, String childField) {
        JsonNode parent = root.get(parentField);
        if (parent == null || !parent.isObject()) {
            if (childField == null && root.has(parentField) && root.get(parentField).isObject()) {
                reorderObjectKeys((ObjectNode) root.get(parentField));
            }
            return;
        }
        ObjectNode parentObj = (ObjectNode) parent;
        JsonNode mapNode = childField != null ? parentObj.get(childField) : parentObj;
        if (mapNode == null || !mapNode.isObject()) {
            return;
        }
        ObjectNode sorted = reorderObjectKeys((ObjectNode) mapNode);
        if (childField != null) {
            parentObj.set(childField, sorted);
        }
    }

    private static ObjectNode reorderObjectKeys(ObjectNode mapNode) {
        ObjectNode sorted = MAPPER.createObjectNode();
        TreeMap<String, JsonNode> keys = new TreeMap<>();
        mapNode.fields().forEachRemaining(e -> keys.put(e.getKey(), e.getValue()));
        keys.forEach(sorted::set);
        return sorted;
    }

    public static String canonicalJsonString(JsonNode node) throws java.io.IOException {
        return MAPPER.writeValueAsString(deepCanonicalize(node));
    }

    public static boolean jsonEquals(JsonNode a, JsonNode b) throws java.io.IOException {
        return canonicalJsonString(a).equals(canonicalJsonString(b));
    }

    public static boolean jsonEqualsIgnoringRevision(JsonNode a, JsonNode b) throws java.io.IOException {
        return canonicalJsonString(withoutRevision(a)).equals(canonicalJsonString(withoutRevision(b)));
    }

    public static String mergeMetadata(String timelineJson, Map<String, String> additions)
            throws java.io.IOException {
        if (additions == null || additions.isEmpty()) {
            return timelineJson;
        }
        ObjectNode root = (ObjectNode) parse(timelineJson);
        ObjectNode metadata = root.has("metadata") && root.get("metadata").isObject()
                ? (ObjectNode) root.get("metadata")
                : MAPPER.createObjectNode();
        additions.forEach(metadata::put);
        root.set("metadata", metadata);
        return write(root);
    }

    private static JsonNode withoutRevision(JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }
        ObjectNode copy = ((ObjectNode) node).deepCopy();
        copy.remove("revision");
        return copy;
    }
}
