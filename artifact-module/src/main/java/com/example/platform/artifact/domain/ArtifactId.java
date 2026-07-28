package com.example.platform.artifact.domain;

import java.io.Serializable;

/**
 * Business/source identity of an Artifact.
 *
 * <p>Identity is independent of content: two artifacts with identical bytes but different
 * source intent carry different {@link ArtifactId}s. New content — even a single-byte
 * change — yields a new Artifact with a new id (immutability / no last-write-wins).
 */
public record ArtifactId(String value) implements Serializable {

    public ArtifactId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ArtifactId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
