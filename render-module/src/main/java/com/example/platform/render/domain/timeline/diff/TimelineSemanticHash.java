package com.example.platform.render.domain.timeline.diff;

/**
 * Content-addressed semantic hash for a timeline snapshot.
 * Internal domain model. Stable identity for cache/dedup.
 */
public record TimelineSemanticHash(String value) {
    public TimelineSemanticHash {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineSemanticHash must not be blank");
    }
}
