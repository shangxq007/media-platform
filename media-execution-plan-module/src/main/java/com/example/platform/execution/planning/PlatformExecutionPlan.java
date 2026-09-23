package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Platform-owned admission contract shared by all planning domains. */
public record PlatformExecutionPlan(
        Scope scope,
        SourceRevision source,
        ExecutionPlanId planId,
        OperationIdentity operation,
        List<TypedInputReference> inputs,
        List<TypedOutputExpectation> outputs,
        ExecutionMode executionMode,
        IdempotencyIdentity idempotency,
        EntitlementQuotaSnapshot quota,
        CorrelationAuditIdentity audit) {
    public PlatformExecutionPlan {
        Objects.requireNonNull(scope); Objects.requireNonNull(source); Objects.requireNonNull(planId);
        Objects.requireNonNull(operation); Objects.requireNonNull(inputs); Objects.requireNonNull(outputs);
        Objects.requireNonNull(executionMode); Objects.requireNonNull(idempotency);
        Objects.requireNonNull(quota); Objects.requireNonNull(audit);
        inputs = List.copyOf(inputs); outputs = List.copyOf(outputs);
        if (inputs.isEmpty() || outputs.isEmpty()) throw new IllegalArgumentException("execution plan requires typed IO");
    }
    public record Scope(String tenantId, String workspaceId, String actorId) {
        public Scope { required(tenantId, "tenantId"); required(workspaceId, "workspaceId"); required(actorId, "actorId"); }
    }
    public record SourceRevision(String domain, String sourceId, String revision) {
        public SourceRevision { required(domain, "source domain"); required(sourceId, "sourceId"); required(revision, "source revision"); }
    }
    public record OperationIdentity(String capability, String operation) {
        public OperationIdentity { required(capability, "capability"); required(operation, "operation"); }
    }
    public record TypedInputReference(String name, String contract, String contractVersion, String reference) {
        public TypedInputReference { required(name, "input name"); required(contract, "input contract"); required(contractVersion, "input contractVersion"); required(reference, "input reference"); }
    }
    public record TypedOutputExpectation(String name, String contract, String contractVersion, String materialization) {
        public TypedOutputExpectation { required(name, "output name"); required(contract, "output contract"); required(contractVersion, "output contractVersion"); required(materialization, "output materialization"); }
    }
    public enum ExecutionMode { SYNCHRONOUS, ASYNCHRONOUS, BATCH }
    public record IdempotencyIdentity(String key, String requestHash) {
        public IdempotencyIdentity { required(key, "idempotency key"); required(requestHash, "request hash"); }
    }
    public record EntitlementQuotaSnapshot(String snapshotId, Map<String, String> entitlements, long quotaUnits) {
        public EntitlementQuotaSnapshot { required(snapshotId, "quota snapshot"); if (quotaUnits < 0) throw new IllegalArgumentException("quotaUnits must be non-negative"); entitlements = entitlements == null ? Map.of() : Map.copyOf(entitlements); }
    }
    public record CorrelationAuditIdentity(String correlationId, String auditId) {
        public CorrelationAuditIdentity { required(correlationId, "correlationId"); required(auditId, "auditId"); }
    }
    private static void required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
}
