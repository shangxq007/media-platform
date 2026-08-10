package com.example.platform.execution.domain;

import com.example.platform.artifact.domain.ArtifactId;
import com.example.platform.storage.contract.ContentDigest;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds an input artifact to an execution plan.
 *
 * <p>Immutable value object that declares: which artifact is expected,
 * its expected content digest, byte length, media type, and the semantic
 * role it plays in the plan.
 *
 * <p>Input bindings are validated at plan construction time — the artifact
 * must exist, be in AVAILABLE state, and match the expected digest/media type.
 */
public record ExecutionInputBinding(
        ExecutionInputId inputId,
        ArtifactId artifactId,
        ContentDigest expectedContentDigest,
        long expectedByteLength,
        String expectedMediaType,
        ExecutionInputRole inputRole,
        boolean required
) implements Serializable {

    public ExecutionInputBinding {
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(expectedContentDigest, "expectedContentDigest");
        if (expectedByteLength < 0) throw new IllegalArgumentException("expectedByteLength must be non-negative");
        Objects.requireNonNull(expectedMediaType, "expectedMediaType");
        if (expectedMediaType.isBlank()) throw new IllegalArgumentException("expectedMediaType must not be blank");
        Objects.requireNonNull(inputRole, "inputRole");
    }

    /**
     * Creates a required primary media input binding.
     */
    public static ExecutionInputBinding primaryMedia(
            ExecutionInputId inputId,
            ArtifactId artifactId,
            ContentDigest digest,
            long byteLength,
            String mediaType) {
        return new ExecutionInputBinding(inputId, artifactId, digest, byteLength, mediaType, ExecutionInputRole.PRIMARY_MEDIA, true);
    }

    /**
     * Creates an optional secondary input binding.
     */
    public static ExecutionInputBinding optional(
            ExecutionInputId inputId,
            ArtifactId artifactId,
            ContentDigest digest,
            long byteLength,
            String mediaType,
            ExecutionInputRole role) {
        return new ExecutionInputBinding(inputId, artifactId, digest, byteLength, mediaType, role, false);
    }

    /**
     * Returns true if this input is required for plan execution.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "input{" +
                "id=" + inputId.value() +
                ",artifact=" + artifactId.value() +
                ",digest=" + expectedContentDigest.canonicalValue() +
                ",bytes=" + expectedByteLength +
                ",media=" + expectedMediaType +
                ",role=" + inputRole.name() +
                ",required=" + required +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
