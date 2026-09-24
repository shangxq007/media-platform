package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.CanonicalSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

/** Canonical JSON and fingerprint rules for all platform-owned Artifact contracts. */
public final class ArtifactContractCanonicalizer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ArtifactContractCanonicalizer() {}

    public static String canonicalJson(JsonNode value) {
        if (value == null || value.isNull()) return "null";
        if (value.isObject()) {
            ObjectNode sorted = MAPPER.createObjectNode();
            var fields = new ArrayList<Map.Entry<String, JsonNode>>();
            value.fields().forEachRemaining(fields::add);
            fields.sort(Map.Entry.comparingByKey());
            fields.forEach(e -> sorted.set(e.getKey(), sortedNode(e.getValue())));
            return sorted.toString();
        }
        if (value.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            value.forEach(v -> array.add(sortedNode(v)));
            return array.toString();
        }
        return value.toString();
    }

    public static String fingerprint(JsonNode value) {
        return CanonicalSerializer.sha256Hex(canonicalJson(value));
    }

    private static JsonNode sortedNode(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) return value;
        try {
            return MAPPER.readTree(canonicalJson(value));
        } catch (Exception e) {
            throw new ArtifactContractException(ArtifactContractErrorCode.INVALID_FINGERPRINT,
                    "contract value cannot be canonicalized");
        }
    }
}
