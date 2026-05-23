package com.example.platform.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

public final class Jsons {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Jsons() {}
    public static String toJson(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try { return MAPPER.readValue(json, typeRef); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public static <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}