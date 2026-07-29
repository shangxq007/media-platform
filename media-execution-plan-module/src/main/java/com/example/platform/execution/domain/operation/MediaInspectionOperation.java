package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Operation to inspect source media and extract metadata.
 *
 * <p>Corresponds to {@link ExecutionStepKind#INSPECT}.
 * Produces codec info, duration, resolution, frame rate, etc.
 */
public record MediaInspectionOperation(
        Set<String> inspectionTargets,
        boolean extractProbeData,
        boolean extractThumbnails
) implements Serializable, MediaOperation {

    public MediaInspectionOperation {
        Objects.requireNonNull(inspectionTargets, "inspectionTargets");
        inspectionTargets = Set.copyOf(inspectionTargets);
    }

    /**
     * Creates an inspection operation that extracts all available metadata.
     */
    public static MediaInspectionOperation fullInspection() {
        return new MediaInspectionOperation(
                Set.of("codec", "duration", "resolution", "framerate", "bitrate", "audio"),
                true, false);
    }

    /**
     * Creates a minimal inspection operation for basic codec info only.
     */
    public static MediaInspectionOperation minimal() {
        return new MediaInspectionOperation(Set.of("codec"), false, false);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.INSPECT;
    }

    @Override
    public String operationType() {
        return "INSPECT";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "inspect{" +
                "targets=" + inspectionTargets.stream().sorted().toList() +
                ",probe=" + extractProbeData +
                ",thumb=" + extractThumbnails +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
