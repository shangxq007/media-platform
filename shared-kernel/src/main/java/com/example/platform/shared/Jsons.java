package com.example.platform.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Collections;
import java.util.List;

/**
 * The platform's single bounded JSON serialization boundary codec for
 * cross-module payloads (K2-02 authority decision).
 *
 * <p><strong>Authority contract</strong>: {@code Jsons} exists to keep the
 * wire representation of cross-module payloads consistent — outbox event
 * payloads (e.g. {@code artifact.created} carrying {@link java.time.Instant}
 * values), notification event payloads, and domain JSON columns. It is a
 * serialization <em>boundary</em>, not a generic JSON platform: it is never
 * extended into a universal codec authority, and modules with specialized
 * serialization needs must use their own infrastructure.</p>
 *
 * <p><strong>Java-time correctness (K2-02)</strong>: the mapper registers
 * {@link JavaTimeModule} and writes java.time values as ISO-8601 strings
 * ({@code WRITE_DATES_AS_TIMESTAMPS} disabled), matching the platform's
 * Spring-configured serialization convention. Baseline defect (proven):
 * an unregistered mapper threw {@code InvalidDefinitionException} for any
 * {@code Instant} payload, which made the production-reachable
 * {@code artifact.created} outbox path fail at runtime. Deserialization
 * remains lenient (accepts ISO-8601 and numeric forms).</p>
 *
 * <p>Failure semantics are unchanged: serialization/deserialization errors
 * surface as {@link IllegalStateException} (fail-closed).</p>
 */
public final class Jsons {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
