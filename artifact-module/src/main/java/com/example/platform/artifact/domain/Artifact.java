package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.shared.digest.ContentDigest;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Artifact record — the core identity for a media artifact on the platform.
 *
 * <p>Once created, these fields CANNOT change: artifactId, tenantId, contentDigest,
 * byteLength, mediaType, artifactKind, schemaVersion, createdAt. New content → new Artifact.
 * No last-write-wins metadata mutation.
 *
 * <p>All fields are non-null and validated at construction.
 */
public record Artifact(
        ArtifactId artifactId,
        String tenantId,
        ContentDigest contentDigest,
        long byteLength,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        ArtifactState state,
        int schemaVersion,
        Instant createdAt
) implements Serializable {

    public Artifact {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (byteLength < 0) throw new IllegalArgumentException("byteLength must be non-negative");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(state, "state");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Schema version for the current Artifact and Provenance V1 model.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Returns a new Artifact with the given state (does not mutate this instance).
     */
    public Artifact withState(ArtifactState newState) {
        return new Artifact(artifactId, tenantId, contentDigest, byteLength, mediaType, artifactKind, newState, schemaVersion, createdAt);
    }

    /**
     * Canonical serialization for deterministic hashing/digests.
     * Fields are serialized in declaration order with stable enum name() representation.
     */
    public String canonicalForm() {
        return "artifact{" +
                "id=" + artifactId.value() +
                ",tenant=" + tenantId +
                ",digest=" + contentDigest.canonicalValue() +
                ",bytes=" + byteLength +
                ",media=" + mediaType.name() +
                ",kind=" + artifactKind.name() +
                ",state=" + state.name() +
                ",schema=" + schemaVersion +
                ",created=" + createdAt.toString() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
