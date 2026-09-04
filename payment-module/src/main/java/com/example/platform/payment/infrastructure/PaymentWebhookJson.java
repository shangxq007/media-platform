package com.example.platform.payment.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class PaymentWebhookJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private PaymentWebhookJson() {}
    static <T> T fromJson(String value, TypeReference<T> type) { try { return MAPPER.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); } }
}
