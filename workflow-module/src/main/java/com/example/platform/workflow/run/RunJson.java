package com.example.platform.workflow.run;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RunJson {
    private RunJson() {}

    public static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .findAndAddModules()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid run value", e);
        }
    }

    public static <T> T read(String value, Class<T> type) {
        try {
            return MAPPER.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid run value", e);
        }
    }

    public static String digest(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> result = new TreeMap<>();
            node.properties().forEach(e -> result.put(e.getKey(), canonical(e.getValue())));
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }

    public static Map<String, String> inputs(String json) {
        var node = read(json, JsonNode.class);
        if (!node.isObject()) throw new IllegalArgumentException("Inputs must be an object");
        Map<String, String> values = new TreeMap<>();
        node.properties().forEach(e -> values.put(e.getKey(), write(canonical(e.getValue()))));
        return Map.copyOf(values);
    }
}
