package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

/**
 * Base interface for all typed media operations.
 *
 * <p>Closed, version-governed hierarchy — new operation types require schema version bump.
 * Each operation type corresponds to a specific {@link ExecutionStepKind}.
 *
 * <p>Operations are immutable value objects that describe WHAT to do, not HOW.
 * Backend-specific command generation is the responsibility of a future compiler.
 */
public sealed interface MediaOperation permits
        MediaInspectionOperation,
        DecodeOperation,
        TrimOperation,
        ScaleOperation,
        CropOperation,
        AudioMixOperation,
        ComposeOperation,
        TranscodeOperation,
        ThumbnailOperation,
        WaveformOperation,
        SubtitleBurnInOperation,
        AnalysisOperation,
        GeneratedMediaOperation,
        PackageOperation,
        IntegrityVerificationOperation {

    /**
     * Returns the step kind this operation belongs to.
     */
    ExecutionStepKind stepKind();

    /**
     * Returns the operation type name for serialization.
     */
    String operationType();

    /**
     * Returns the schema version of this operation type.
     */
    int schemaVersion();

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    String canonicalForm();
}
