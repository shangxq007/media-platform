package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable Media Execution Plan — the core domain object.
 *
 * <p>Represents an immutable computation DAG that expresses accepted media domain
 * state and artifact inputs as a deterministic, verifiable execution plan.
 *
 * <p>The plan is:
 * <ul>
 *   <li>Immutable — once created, fields cannot change</li>
 *   <li>Deterministic — same semantic plan yields same digest regardless of insertion order</li>
 *   <li>Backend-neutral — no reference to FFmpeg, Kubernetes, OpenCue, etc.</li>
 *   <li>Tenant-scoped — all artifacts must belong to the plan's tenant</li>
 * </ul>
 */
public record MediaExecutionPlan(
        ExecutionPlanId planId,
        String tenantId,
        String productId,
        String timelineRevisionId,
        String timelineRevisionDigest,
        ExecutionPlanSchemaVersion schemaVersion,
        List<ExecutionInputBinding> inputs,
        List<MediaExecutionStep> steps,
        List<ExecutionDependency> edges,
        List<ExecutionOutputDeclaration> outputs,
        ExecutionCreationContext creationContext,
        ExecutionPlanDigest digest
) implements Serializable {

    public MediaExecutionPlan {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        Objects.requireNonNull(productId, "productId");
        if (productId.isBlank()) throw new IllegalArgumentException("productId must not be blank");
        Objects.requireNonNull(timelineRevisionId, "timelineRevisionId");
        if (timelineRevisionId.isBlank()) throw new IllegalArgumentException("timelineRevisionId must not be blank");
        Objects.requireNonNull(timelineRevisionDigest, "timelineRevisionDigest");
        if (timelineRevisionDigest.isBlank()) throw new IllegalArgumentException("timelineRevisionDigest must not be blank");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(inputs, "inputs");
        inputs = List.copyOf(inputs);
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
        Objects.requireNonNull(edges, "edges");
        edges = List.copyOf(edges);
        Objects.requireNonNull(outputs, "outputs");
        outputs = List.copyOf(outputs);
        Objects.requireNonNull(creationContext, "creationContext");
        Objects.requireNonNull(digest, "digest");
    }

    /**
     * Returns the number of steps in the plan.
     */
    public int stepCount() {
        return steps.size();
    }

    /**
     * Returns the number of dependency edges in the plan.
     */
    public int edgeCount() {
        return edges.size();
    }

    /**
     * Returns the number of input bindings.
     */
    public int inputCount() {
        return inputs.size();
    }

    /**
     * Returns the number of output declarations.
     */
    public int outputCount() {
        return outputs.size();
    }

    /**
     * Returns true if the plan has no steps (empty plan).
     */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * Returns the set of step IDs that have no incoming dependencies (roots).
     */
    public Set<ExecutionStepId> rootStepIds() {
        Set<ExecutionStepId> targets = edges.stream()
                .map(ExecutionDependency::toStepId)
                .collect(java.util.stream.Collectors.toSet());
        return steps.stream()
                .map(MediaExecutionStep::stepId)
                .filter(id -> !targets.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the set of step IDs that have no outgoing dependencies (sinks).
     */
    public Set<ExecutionStepId> sinkStepIds() {
        Set<ExecutionStepId> sources = edges.stream()
                .map(ExecutionDependency::fromStepId)
                .collect(java.util.stream.Collectors.toSet());
        return steps.stream()
                .map(MediaExecutionStep::stepId)
                .filter(id -> !sources.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Returns a canonical string representation for deterministic hashing.
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder("plan{");
        sb.append("id=").append(planId.value());
        sb.append(",tenant=").append(tenantId);
        sb.append(",product=").append(productId);
        sb.append(",rev=").append(timelineRevisionId);
        sb.append(",revDigest=").append(timelineRevisionDigest);
        sb.append(",schema=").append(schemaVersion.value());
        sb.append(",inputs=").append(inputs.stream()
                .map(ExecutionInputBinding::canonicalForm)
                .sorted()
                .toList());
        sb.append(",steps=").append(steps.stream()
                .map(MediaExecutionStep::canonicalForm)
                .sorted()
                .toList());
        sb.append(",edges=").append(edges.stream()
                .map(ExecutionDependency::canonicalForm)
                .sorted()
                .toList());
        sb.append(",outputs=").append(outputs.stream()
                .map(ExecutionOutputDeclaration::canonicalForm)
                .sorted()
                .toList());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toString() {
        return "MediaExecutionPlan{" +
                "planId=" + planId +
                ", tenantId='" + tenantId + '\'' +
                ", steps=" + steps.size() +
                ", edges=" + edges.size() +
                ", digest=" + digest +
                '}';
    }
}
