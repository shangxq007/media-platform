package com.example.platform.render.domain.timeline.version;

import java.util.Map;
import java.util.Set;

/**
 * Safe metadata for a timeline commit.
 * Internal domain model. No secrets, no provider/storage/backend fields.
 */
public record TimelineCommitMetadata(
        String authorRef,
        String message,
        Map<String, String> safeMetadata) {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "providerName", "backendName",
            "secret", "password", "token", "apiKey");

    public TimelineCommitMetadata {
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
        validateField(authorRef);
        validateField(message);
        for (Map.Entry<String, String> entry : safeMetadata.entrySet()) {
            validateField(entry.getKey());
            validateField(entry.getValue());
        }
    }

    private static void validateField(String value) {
        if (value == null) return;
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (value.toLowerCase().contains(keyword))
                throw new IllegalArgumentException("Commit metadata must not contain: " + keyword);
        }
    }
}
