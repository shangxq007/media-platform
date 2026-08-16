package com.example.platform.timeline.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Deterministic content digester for TimelineDocument.
 */
public class TimelineContentDigester {

    private final ObjectMapper mapper;

    public TimelineContentDigester() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // CANONICAL_TIMELINE_SERIALIZATION_V2: deterministic byte-for-byte
                // serialization (stable property order) for revision content hashing.
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public String digest(TimelineDocument document) {
        try {
            byte[] jsonBytes = mapper.writeValueAsBytes(document);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(jsonBytes);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute timeline digest", e);
        }
    }

    public String getSerializationRules() {
        return "SHA-256 of canonical JSON (deterministic)";
    }

    public String getAlgorithm() {
        return "SHA-256";
    }
}
