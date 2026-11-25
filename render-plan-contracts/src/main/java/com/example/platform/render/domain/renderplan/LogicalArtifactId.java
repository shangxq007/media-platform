package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Deterministic logical identity for a to-be-produced intermediate artifact (C15).
 * Derived from node id + output role; NEVER random. Becomes a persisted
 * ArtifactId at execution registration — never pre-created.
 */
public record LogicalArtifactId(String value) {

    public LogicalArtifactId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LogicalArtifactId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
