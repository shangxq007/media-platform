package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Resource requirements for an execution step.
 *
 * <p>Immutable value object describing the computational resources needed
 * to execute a single step. Used for scheduling, capacity planning, and
 * execution provider selection.
 */
public record ExecutionResourceRequirement(
        CpuClass cpuClass,
        int minimumCpuCores,
        MemoryClass memoryClass,
        long minimumMemoryBytes,
        TemporaryStorageClass temporaryStorageClass,
        long estimatedTemporaryBytes,
        NetworkRequirement networkRequirement
) implements Serializable {

    public ExecutionResourceRequirement {
        Objects.requireNonNull(cpuClass, "cpuClass");
        if (minimumCpuCores < 0) throw new IllegalArgumentException("minimumCpuCores must be non-negative");
        Objects.requireNonNull(memoryClass, "memoryClass");
        if (minimumMemoryBytes < 0) throw new IllegalArgumentException("minimumMemoryBytes must be non-negative");
        Objects.requireNonNull(temporaryStorageClass, "temporaryStorageClass");
        if (estimatedTemporaryBytes < 0) throw new IllegalArgumentException("estimatedTemporaryBytes must be non-negative");
        Objects.requireNonNull(networkRequirement, "networkRequirement");
    }



    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "resource{" +
                "cpu=" + cpuClass.name() +
                ",cores=" + minimumCpuCores +
                ",mem=" + memoryClass.name() +
                ",memBytes=" + minimumMemoryBytes +
                ",storage=" + temporaryStorageClass.name() +
                ",tmpBytes=" + estimatedTemporaryBytes +
                ",net=" + networkRequirement.name() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
