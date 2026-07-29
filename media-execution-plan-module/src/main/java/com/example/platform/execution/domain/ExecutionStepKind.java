package com.example.platform.execution.domain;

/**
 * Classifies the kind of work an execution step performs.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 */
public enum ExecutionStepKind {
    /**
     * Inspect source media — extract metadata, codec info, duration, etc.
     */
    INSPECT,
    /**
     * Decode compressed media into raw frames/samples.
     */
    DECODE,
    /**
     * Transform raw media — scale, crop, trim, rotate, etc.
     */
    TRANSFORM,
    /**
     * Compose multiple media sources into a single output.
     */
    COMPOSE,
    /**
     * Analyze media content — scene detection, face recognition, etc.
     */
    ANALYZE,
    /**
     * Generate new media — AI synthesis, procedural generation.
     */
    GENERATE,
    /**
     * Encode raw frames/samples into compressed media.
     */
    ENCODE,
    /**
     * Package media into a container format (MP4, DASH, HLS, etc.).
     */
    PACKAGE,
    /**
     * Verify integrity, quality, or conformance of output.
     */
    VERIFY
}
