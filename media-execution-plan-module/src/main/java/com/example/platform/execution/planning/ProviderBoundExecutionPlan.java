package com.example.platform.execution.planning;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral platform contract between validated domain planning and
 * runtime admission. Provider implementation details stay behind the
 * capability authority; this value carries only immutable binding identity and
 * typed IO needed by later canonical projections.
 */
public record ProviderBoundExecutionPlan(
        PublishedRevision publishedRevision,
        Scope scope,
        List<CapabilityProviderBinding> capabilityBindings,
        List<TypedReference> inputs,
        List<TypedOutputContract> outputs,
        ExecutionMode executionMode,
        EntitlementQuotaSnapshot entitlementQuota,
        PlanFingerprint planFingerprint,
        IdempotencyIdentity idempotency,
        PolicyReferences policies) {

    public ProviderBoundExecutionPlan {
        Objects.requireNonNull(publishedRevision, "publishedRevision");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(capabilityBindings, "capabilityBindings");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(entitlementQuota, "entitlementQuota");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(idempotency, "idempotency");
        Objects.requireNonNull(policies, "policies");
        if (capabilityBindings.isEmpty() || inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("provider-bound plan requires capabilities and typed IO");
        }
        capabilityBindings = List.copyOf(capabilityBindings);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public record PublishedRevision(String domain, String subjectId, String version,
            long revision) {
        public PublishedRevision {
            required(domain, "domain"); required(subjectId, "subjectId");
            required(version, "version");
            if (revision < 1) throw new IllegalArgumentException("published revision must be positive");
        }
    }

    public record Scope(String tenantId, String workspaceId, String actorId) {
        public Scope { required(tenantId, "tenantId"); required(workspaceId, "workspaceId"); required(actorId, "actorId"); }
    }

    public record CapabilityProviderBinding(String nodeId, String capabilityId,
            String capabilityVersion, String providerId, String providerContractVersion,
            String inputContract, String inputContractVersion, String outputContract,
            String outputContractVersion) {
        public CapabilityProviderBinding {
            required(nodeId, "nodeId"); required(capabilityId, "capabilityId");
            required(capabilityVersion, "capabilityVersion"); required(providerId, "providerId");
            required(providerContractVersion, "providerContractVersion");
            required(inputContract, "inputContract"); required(inputContractVersion, "inputContractVersion");
            required(outputContract, "outputContract"); required(outputContractVersion, "outputContractVersion");
        }
    }

    public record TypedReference(String name, String contract, String contractVersion,
            String reference) {
        public TypedReference {
            required(name, "reference name"); required(contract, "contract");
            required(contractVersion, "contractVersion"); required(reference, "reference");
        }
    }

    public record TypedOutputContract(String name, String contract, String contractVersion,
            String materializationKind) {
        public TypedOutputContract {
            required(name, "output name"); required(contract, "contract");
            required(contractVersion, "contractVersion"); required(materializationKind, "materializationKind");
        }
    }

    public enum ExecutionMode { SYNCHRONOUS, ASYNCHRONOUS, BATCH }

    public record EntitlementQuotaSnapshot(String snapshotId, Map<String, String> entitlements,
            long quotaUnits) {
        public EntitlementQuotaSnapshot {
            required(snapshotId, "snapshotId");
            if (quotaUnits < 0) throw new IllegalArgumentException("quotaUnits must be non-negative");
            entitlements = entitlements == null ? Map.of() : Map.copyOf(entitlements);
        }
    }

    public record PlanFingerprint(String value) { public PlanFingerprint { required(value, "planFingerprint"); } }
    public record IdempotencyIdentity(String key, String requestHash) {
        public IdempotencyIdentity { required(key, "idempotencyKey"); required(requestHash, "requestHash"); }
    }
    public record PolicyReferences(String cancellationPolicy, String retryPolicy) {
        public PolicyReferences { required(cancellationPolicy, "cancellationPolicy"); required(retryPolicy, "retryPolicy"); }
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
