package com.example.platform.execution.domain;

/**
 * CPU performance class for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * Higher classes indicate greater CPU resource needs.
 */
public enum CpuClass {
    /**
     * Minimal CPU — lightweight operations like metadata inspection.
     */
    MINIMAL,
    /**
     * Low CPU — simple transforms, scaling.
     */
    LOW,
    /**
     * Standard CPU — typical encoding/decoding.
     */
    STANDARD,
    /**
     * High CPU — complex analysis, multi-pass encoding.
     */
    HIGH,
    /**
     * Compute-intensive CPU — AI inference, complex effects.
     */
    COMPUTE_INTENSIVE
}
