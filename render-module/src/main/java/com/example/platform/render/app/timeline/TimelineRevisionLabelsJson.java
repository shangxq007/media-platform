package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Normalizes and serializes revision label arrays stored in {@code labels_json}. */
public final class TimelineRevisionLabelsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    static final int MAX_LABELS = 8;
    static final int MAX_LABEL_LENGTH = 32;

    private TimelineRevisionLabelsJson() {}

    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw = MAPPER.readValue(json, STRING_LIST);
            return normalize(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toJson(List<String> labels) {
        List<String> normalized = normalize(labels);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> normalize(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String trimmed = label.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_LABEL_LENGTH) {
                trimmed = trimmed.substring(0, MAX_LABEL_LENGTH);
            }
            seen.add(trimmed);
            if (seen.size() >= MAX_LABELS) {
                break;
            }
        }
        return new ArrayList<>(seen);
    }
}
