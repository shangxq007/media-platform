package com.example.platform.execution.planning;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.math.BigDecimal;

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
        List<TypedReference> resources,
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
        Objects.requireNonNull(resources, "resources");
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
        resources = List.copyOf(resources);
        outputs = List.copyOf(outputs);
    }

    /** Compatibility constructor for older planner callers that had no resource evidence. */
    public ProviderBoundExecutionPlan(PublishedRevision publishedRevision, Scope scope,
            List<CapabilityProviderBinding> capabilityBindings, List<TypedReference> inputs,
            List<TypedOutputContract> outputs, ExecutionMode executionMode,
            EntitlementQuotaSnapshot entitlementQuota, PlanFingerprint planFingerprint,
            IdempotencyIdentity idempotency, PolicyReferences policies) {
        this(publishedRevision, scope, capabilityBindings, inputs, List.of(), outputs,
                executionMode, entitlementQuota, planFingerprint, idempotency, policies);
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
            String capabilityVersion, ProviderIdentity providerIdentity,
            String providerContractVersion,
            String inputContract, String inputContractVersion, String outputContract,
            String outputContractVersion) {
        public CapabilityProviderBinding {
            required(nodeId, "nodeId"); required(capabilityId, "capabilityId");
            required(capabilityVersion, "capabilityVersion"); Objects.requireNonNull(providerIdentity, "providerIdentity");
            required(providerContractVersion, "providerContractVersion");
            required(inputContract, "inputContract"); required(inputContractVersion, "inputContractVersion");
            required(outputContract, "outputContract"); required(outputContractVersion, "outputContractVersion");
        }
    }

    /** Opaque registry reference; provider implementation identity never crosses this boundary. */
    public record ProviderIdentity(String registryReference) {
        public ProviderIdentity { required(registryReference, "provider registry reference"); }
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
            BigDecimal quotaUnits, BigDecimal quotaRemaining) {
        public EntitlementQuotaSnapshot {
            required(snapshotId, "snapshotId");
            Objects.requireNonNull(quotaUnits, "quotaUnits");
            if (quotaUnits.signum() < 0) throw new IllegalArgumentException("quotaUnits must be non-negative");
            Objects.requireNonNull(quotaRemaining, "quotaRemaining");
            if (quotaRemaining.signum() < 0) throw new IllegalArgumentException("quotaRemaining must be non-negative");
            entitlements = entitlements == null ? Map.of() : Map.copyOf(entitlements);
        }
        public EntitlementQuotaSnapshot(String snapshotId, Map<String, String> entitlements, BigDecimal quotaUnits) {
            this(snapshotId, entitlements, quotaUnits, quotaUnits);
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
