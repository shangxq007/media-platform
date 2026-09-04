package com.example.platform.entitlement.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class EntitlementPersistenceJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private EntitlementPersistenceJson() {}
    static String toJson(Object value) { try { return MAPPER.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    static <T> T fromJson(String value, Class<T> type) { try { return MAPPER.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
}
