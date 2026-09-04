package com.example.platform.policy.featureflag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Collections;
import java.util.List;

final class FeatureFlagPersistenceJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private FeatureFlagPersistenceJson() {}
    static String toJson(Object value) { try { return MAPPER.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    static <T> T fromJson(String value, Class<T> type) { try { return MAPPER.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
    static <T> List<T> fromJsonList(String value, Class<T> type) { if (value == null || value.isBlank()) return Collections.emptyList(); try { return MAPPER.readValue(value, MAPPER.getTypeFactory().constructCollectionType(List.class, type)); } catch (Exception e) { throw new IllegalStateException(e); } }
}
