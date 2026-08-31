package com.example.platform.timeline.canonical;

import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Deterministic content digester for TimelineDocument.
 */
public class TimelineContentDigester {

    public String digest(TimelineDocument document) {
        try {
            byte[] jsonBytes = TimelineDocumentJsonSerializer.serialize(document)
                    .getBytes(StandardCharsets.UTF_8);
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
