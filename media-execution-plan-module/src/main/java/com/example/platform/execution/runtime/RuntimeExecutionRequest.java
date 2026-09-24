package com.example.platform.execution.runtime;

import com.example.platform.execution.domain.ExecutionPlanId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Platform-owned runtime facts derived after planning/admission. Worker observations remain separate. */
public record RuntimeExecutionRequest(
        String executionId,
        ExecutionPlanId planId,
        String capabilityId,
        String capabilityVersion,
        String resolvedInputContract,
        String resolvedOutputContract,
        Scope scope,
        ExecutionMode executionMode,
        PlacementConstraints placement,
        RuntimeEligibilityEvidence eligibility,
        long ownershipGeneration,
        Idempotency idempotency,
        List<TypedReference> inputs,
        List<TypedReference> outputs) {
    public RuntimeExecutionRequest {
        required(executionId, "executionId"); Objects.requireNonNull(planId); required(capabilityId, "capabilityId");
        required(capabilityVersion, "capabilityVersion"); required(resolvedInputContract, "resolvedInputContract");
        required(resolvedOutputContract, "resolvedOutputContract"); Objects.requireNonNull(scope);
        Objects.requireNonNull(executionMode); Objects.requireNonNull(placement); Objects.requireNonNull(eligibility);
        if (ownershipGeneration < 0) throw new IllegalArgumentException("ownershipGeneration must be non-negative");
        Objects.requireNonNull(idempotency); inputs = List.copyOf(inputs); outputs = List.copyOf(outputs);
        if (inputs.isEmpty() || outputs.isEmpty()) throw new IllegalArgumentException("runtime request requires typed IO");
    }
    public record Scope(String tenantId, String workspaceId, String actorId) { public Scope { required(tenantId,"tenantId"); required(workspaceId,"workspaceId"); required(actorId,"actorId"); } }
    public record PlacementConstraints(String backend, String region, Map<String,String> constraints) { public PlacementConstraints { required(backend,"backend"); required(region,"region"); constraints = constraints == null ? Map.of() : Map.copyOf(constraints); } }
    public record RuntimeEligibilityEvidence(String workerClass, String runtimeClass, String evidenceId) { public RuntimeEligibilityEvidence { required(workerClass,"workerClass"); required(runtimeClass,"runtimeClass"); required(evidenceId,"evidenceId"); } }
    public record Idempotency(String key, String requestHash) { public Idempotency { required(key,"idempotency key"); required(requestHash,"request hash"); } }
    public record TypedReference(String name, String contract, String version, String reference) { public TypedReference { required(name,"name"); required(contract,"contract"); required(version,"version"); required(reference,"reference"); } }
    public enum ExecutionMode { SYNCHRONOUS, ASYNCHRONOUS, BATCH }
    private static void required(String v, String n) { if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " is required"); }
}
