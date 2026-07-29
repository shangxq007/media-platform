package com.example.platform.execution.domain;

/**
 * Memory performance class for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * Higher classes indicate greater memory resource needs.
 */
public enum MemoryClass {
    /**
     * Minimal memory — lightweight operations.
     */
    MINIMAL,
    /**
     * Low memory — simple operations with small buffers.
     */
    LOW,
    /**
     * Standard memory — typical encoding/decoding.
     */
    STANDARD,
    /**
     * High memory — large frame buffers, multi-stream processing.
     */
    HIGH,
    /**
     * Very high memory — large models, 4K+ processing.
     */
    VERY_HIGH
}
