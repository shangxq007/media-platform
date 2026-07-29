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
        GpuRequirement gpuRequirement,
        TemporaryStorageClass temporaryStorageClass,
        long estimatedTemporaryBytes,
        NetworkRequirement networkRequirement
) implements Serializable {

    public ExecutionResourceRequirement {
        Objects.requireNonNull(cpuClass, "cpuClass");
        if (minimumCpuCores < 0) throw new IllegalArgumentException("minimumCpuCores must be non-negative");
        Objects.requireNonNull(memoryClass, "memoryClass");
        if (minimumMemoryBytes < 0) throw new IllegalArgumentException("minimumMemoryBytes must be non-negative");
        Objects.requireNonNull(gpuRequirement, "gpuRequirement");
        Objects.requireNonNull(temporaryStorageClass, "temporaryStorageClass");
        if (estimatedTemporaryBytes < 0) throw new IllegalArgumentException("estimatedTemporaryBytes must be non-negative");
        Objects.requireNonNull(networkRequirement, "networkRequirement");
    }

    /**
     * Creates a minimal resource requirement for lightweight operations.
     */
    public static ExecutionResourceRequirement minimal() {
        return new ExecutionResourceRequirement(
                CpuClass.MINIMAL, 1,
                MemoryClass.MINIMAL, 64L * 1024 * 1024,
                GpuRequirement.NONE,
                TemporaryStorageClass.MINIMAL,
                0L,
                NetworkRequirement.NONE
        );
    }

    /**
     * Creates a standard resource requirement for typical encoding/decoding.
     */
    public static ExecutionResourceRequirement standard() {
        return new ExecutionResourceRequirement(
                CpuClass.STANDARD, 2,
                MemoryClass.STANDARD, 512L * 1024 * 1024,
                GpuRequirement.OPTIONAL,
                TemporaryStorageClass.STANDARD,
                1024L * 1024 * 1024,
                NetworkRequirement.NONE
        );
    }

    /**
     * Creates a high-performance resource requirement for complex operations.
     */
    public static ExecutionResourceRequirement highPerformance() {
        return new ExecutionResourceRequirement(
                CpuClass.HIGH, 8,
                MemoryClass.HIGH, 4L * 1024 * 1024 * 1024,
                GpuRequirement.REQUIRED,
                TemporaryStorageClass.HIGH,
                10L * 1024 * 1024 * 1024,
                NetworkRequirement.PREFERRED
        );
    }

    /**
     * Returns true if this requirement specifies any GPU need.
     */
    public boolean requiresGpu() {
        return gpuRequirement == GpuRequirement.REQUIRED || gpuRequirement == GpuRequirement.OPTIONAL;
    }

    /**
     * Returns true if this requirement specifies mandatory GPU.
     */
    public boolean requiresMandatoryGpu() {
        return gpuRequirement == GpuRequirement.REQUIRED;
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
                ",gpu=" + gpuRequirement.name() +
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
