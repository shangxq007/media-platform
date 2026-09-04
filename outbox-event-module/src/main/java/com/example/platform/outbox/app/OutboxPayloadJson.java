package com.example.platform.outbox.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class OutboxPayloadJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private OutboxPayloadJson() {}
    static String toJson(Object value) { try { return MAPPER.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
}
