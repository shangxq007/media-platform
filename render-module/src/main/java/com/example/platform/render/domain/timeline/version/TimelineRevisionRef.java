package com.example.platform.render.domain.timeline.version;

import java.util.Set;

/**
 * Semantic reference to a timeline revision.
 * Internal domain model. No storage paths, no provider/backend fields.
 */
public record TimelineRevisionRef(String value) {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "providerName", "backendName",
            "executionEnvironment", "autoDispatch", "rawCommand", "processEnvironment");

    public TimelineRevisionRef {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineRevisionRef must not be blank");
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (value.contains(keyword))
                throw new IllegalArgumentException("TimelineRevisionRef must not contain: " + keyword);
        }
    }
}
