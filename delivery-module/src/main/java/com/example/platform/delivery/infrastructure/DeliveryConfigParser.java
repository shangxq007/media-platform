package com.example.platform.delivery.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class DeliveryConfigParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DeliveryConfigParser() {}

    public static Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid destination config JSON: " + e.getMessage());
        }
    }

    public static Map<String, String> parseCredentials(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String stringVal(Map<String, Object> map, String key) {
        return stringVal(map, key, "");
    }

    public static String stringVal(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : defaultValue;
    }

    public static String toJson(Map<String, ?> map) {
        try {
            return MAPPER.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }

    public static int intVal(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(map.get(key)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
