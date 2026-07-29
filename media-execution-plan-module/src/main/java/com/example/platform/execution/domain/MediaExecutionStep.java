package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaOperation;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A single step in the execution plan DAG.
 *
 * <p>Immutable value object representing one unit of work: what operation to perform,
 * which inputs it consumes, which outputs it produces, resource/capability requirements,
 * determinism classification, and failure policy.
 */
public record MediaExecutionStep(
        ExecutionStepId stepId,
        ExecutionStepKind stepKind,
        MediaOperation operation,
        Set<ExecutionInputId> inputReferences,
        Set<ExecutionOutputId> outputReferences,
        ExecutionResourceRequirement resourceRequirement,
        ExecutionCapabilityRequirement capabilityRequirement,
        ExecutionDeterminism determinism,
        ExecutionStepFailurePolicy failurePolicy
) implements Serializable {

    public MediaExecutionStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(stepKind, "stepKind");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputReferences, "inputReferences");
        inputReferences = Set.copyOf(inputReferences);
        Objects.requireNonNull(outputReferences, "outputReferences");
        outputReferences = Set.copyOf(outputReferences);
        Objects.requireNonNull(resourceRequirement, "resourceRequirement");
        Objects.requireNonNull(capabilityRequirement, "capabilityRequirement");
        Objects.requireNonNull(determinism, "determinism");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
    }

    /**
     * Creates a step with minimal configuration.
     */
    public static MediaExecutionStep of(
            ExecutionStepId stepId,
            MediaOperation operation,
            ExecutionResourceRequirement resources,
            ExecutionCapabilityRequirement capabilities) {
        return new MediaExecutionStep(
                stepId, operation.stepKind(), operation,
                Set.of(), Set.of(),
                resources, capabilities,
                ExecutionDeterminism.DETERMINISTIC,
                ExecutionStepFailurePolicy.FAIL_PLAN);
    }

    /**
     * Creates a step with full configuration.
     */
    public static MediaExecutionStep builder(
            ExecutionStepId stepId,
            MediaOperation operation,
            Set<ExecutionInputId> inputs,
            Set<ExecutionOutputId> outputs,
            ExecutionResourceRequirement resources,
            ExecutionCapabilityRequirement capabilities,
            ExecutionDeterminism determinism,
            ExecutionStepFailurePolicy failurePolicy) {
        return new MediaExecutionStep(
                stepId, operation.stepKind(), operation,
                inputs, outputs,
                resources, capabilities,
                determinism, failurePolicy);
    }

    /**
     * Returns true if this step is deterministic (cacheable).
     */
    public boolean isDeterministic() {
        return determinism == ExecutionDeterminism.DETERMINISTIC;
    }

    /**
     * Returns true if this step has no inputs (root step).
     */
    public boolean isRoot() {
        return inputReferences.isEmpty();
    }

    /**
     * Returns true if this step has no outputs (sink step).
     */
    public boolean isSink() {
        return outputReferences.isEmpty();
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        return "step{" +
                "id=" + stepId.value() +
                ",kind=" + stepKind.name() +
                ",op=" + operation.canonicalForm() +
                ",inputs=" + inputReferences.stream().map(ExecutionInputId::value).sorted().toList() +
                ",outputs=" + outputReferences.stream().map(ExecutionOutputId::value).sorted().toList() +
                ",resources=" + resourceRequirement.canonicalForm() +
                ",capabilities=" + capabilityRequirement.canonicalForm() +
                ",det=" + determinism.name() +
                ",policy=" + failurePolicy.name() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
