package com.example.platform.execution.domain;

/**
 * Temporary storage class for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * Indicates the amount of temporary scratch space needed.
 */
public enum TemporaryStorageClass {
    /**
     * No temporary storage needed.
     */
    NONE,
    /**
     * Minimal temporary storage — metadata, small buffers.
     */
    MINIMAL,
    /**
     * Standard temporary storage — single-pass encoding scratch.
     */
    STANDARD,
    /**
     * High temporary storage — multi-pass encoding, intermediate frames.
     */
    HIGH,
    /**
     * Very high temporary storage — uncompressed video, large intermediate files.
     */
    VERY_HIGH
}
