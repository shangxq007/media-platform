package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * A dependency edge between two execution steps.
 *
 * <p>Immutable value object representing a directed edge in the execution DAG:
 * fromStep must complete before toStep can start. The dependency type
 * classifies the nature of the dependency (data flow, control, validation).
 */
public record ExecutionDependency(
        ExecutionEdgeId edgeId,
        ExecutionStepId fromStepId,
        ExecutionStepId toStepId,
        ExecutionDependencyType dependencyType
) implements Serializable {

    public ExecutionDependency {
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(fromStepId, "fromStepId");
        Objects.requireNonNull(toStepId, "toStepId");
        Objects.requireNonNull(dependencyType, "dependencyType");
        if (fromStepId.equals(toStepId))
            throw new IllegalArgumentException("Self-dependency prohibited: " + fromStepId.value());
    }

    /**
     * Creates a data dependency edge.
     */
    public static ExecutionDependency data(ExecutionEdgeId edgeId, ExecutionStepId from, ExecutionStepId to) {
        return new ExecutionDependency(edgeId, from, to, ExecutionDependencyType.DATA);
    }

    /**
     * Creates a control dependency edge.
     */
    public static ExecutionDependency control(ExecutionEdgeId edgeId, ExecutionStepId from, ExecutionStepId to) {
        return new ExecutionDependency(edgeId, from, to, ExecutionDependencyType.CONTROL);
    }

    /**
     * Creates a validation dependency edge.
     */
    public static ExecutionDependency validation(ExecutionEdgeId edgeId, ExecutionStepId from, ExecutionStepId to) {
        return new ExecutionDependency(edgeId, from, to, ExecutionDependencyType.VALIDATION);
    }

    /**
     * Returns true if this is a data dependency.
     */
    public boolean isDataDependency() {
        return dependencyType == ExecutionDependencyType.DATA;
    }

    /**
     * Returns the source step ID.
     */
    public ExecutionStepId source() {
        return fromStepId;
    }

    /**
     * Returns the target step ID.
     */
    public ExecutionStepId target() {
        return toStepId;
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "edge{" +
                "id=" + edgeId.value() +
                ",from=" + fromStepId.value() +
                ",to=" + toStepId.value() +
                ",type=" + dependencyType.name() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
