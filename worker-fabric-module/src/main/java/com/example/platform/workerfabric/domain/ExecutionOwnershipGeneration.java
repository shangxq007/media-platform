package com.example.platform.workerfabric.domain;

/**
 * Ordered ownership generation within one ExecutableTask lifecycle.
 *
 * <p>The task scope is carried explicitly by every attempt, assignment, and lease that uses this
 * value. It is not a tuple-shaped replacement identity.
 */
public record ExecutionOwnershipGeneration(long value)
        implements Comparable<ExecutionOwnershipGeneration> {

    public ExecutionOwnershipGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("execution ownership generation must be positive");
        }
    }

    public static ExecutionOwnershipGeneration first() {
        return new ExecutionOwnershipGeneration(1);
    }

    public ExecutionOwnershipGeneration next() {
        return new ExecutionOwnershipGeneration(Math.incrementExact(value));
    }

    public boolean isStaleComparedWith(ExecutionOwnershipGeneration current) {
        return compareTo(current) < 0;
    }

    @Override
    public int compareTo(ExecutionOwnershipGeneration other) {
        return Long.compare(value, other.value);
    }
}
