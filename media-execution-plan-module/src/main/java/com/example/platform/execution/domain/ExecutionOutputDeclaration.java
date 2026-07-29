package com.example.platform.execution.domain;

import com.example.platform.artifact.domain.ArtifactKind;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Declares an output artifact that an execution plan will produce.
 *
 * <p>Immutable value object that declares: the output ID, the kind of artifact
 * it will produce, its media type, its semantic role, which step produces it,
 * and any expected properties.
 *
 * <p>Output declarations are validated at plan construction time — the producing
 * step must exist and the output ID must be unique within the plan.
 */
public record ExecutionOutputDeclaration(
        ExecutionOutputId outputId,
        ArtifactKind artifactKind,
        String mediaType,
        ExecutionOutputRole outputRole,
        ExecutionStepId producingStepId,
        Map<String, String> expectedProperties,
        String retentionClass
) implements Serializable {

    public ExecutionOutputDeclaration {
        Objects.requireNonNull(outputId, "outputId");
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(mediaType, "mediaType");
        if (mediaType.isBlank()) throw new IllegalArgumentException("mediaType must not be blank");
        Objects.requireNonNull(outputRole, "outputRole");
        Objects.requireNonNull(producingStepId, "producingStepId");
        expectedProperties = expectedProperties != null ? Map.copyOf(expectedProperties) : Map.of();
    }

    /**
     * Creates a primary output declaration.
     */
    public static ExecutionOutputDeclaration primary(
            ExecutionOutputId outputId,
            ArtifactKind kind,
            String mediaType,
            ExecutionStepId producingStepId) {
        return new ExecutionOutputDeclaration(outputId, kind, mediaType, ExecutionOutputRole.PRIMARY_OUTPUT, producingStepId, Map.of(), "standard");
    }

    /**
     * Creates an intermediate output declaration.
     */
    public static ExecutionOutputDeclaration intermediate(
            ExecutionOutputId outputId,
            ArtifactKind kind,
            String mediaType,
            ExecutionStepId producingStepId) {
        return new ExecutionOutputDeclaration(outputId, kind, mediaType, ExecutionOutputRole.INTERMEDIATE, producingStepId, Map.of(), "temporary");
    }

    /**
     * Creates an output declaration with expected properties.
     */
    public static ExecutionOutputDeclaration withProperties(
            ExecutionOutputId outputId,
            ArtifactKind kind,
            String mediaType,
            ExecutionOutputRole role,
            ExecutionStepId producingStepId,
            Map<String, String> properties) {
        return new ExecutionOutputDeclaration(outputId, kind, mediaType, role, producingStepId, properties, "standard");
    }

    /**
     * Returns true if this is a primary output (final deliverable).
     */
    public boolean isPrimaryOutput() {
        return outputRole == ExecutionOutputRole.PRIMARY_OUTPUT;
    }

    /**
     * Returns true if this is an intermediate output (consumed by later steps).
     */
    public boolean isIntermediate() {
        return outputRole == ExecutionOutputRole.INTERMEDIATE;
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "output{" +
                "id=" + outputId.value() +
                ",kind=" + artifactKind.name() +
                ",media=" + mediaType +
                ",role=" + outputRole.name() +
                ",step=" + producingStepId.value() +
                ",props=" + expectedProperties.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList() +
                ",retention=" + (retentionClass != null ? retentionClass : "") +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
