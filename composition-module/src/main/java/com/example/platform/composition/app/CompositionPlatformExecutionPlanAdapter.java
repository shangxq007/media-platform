package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels;
import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.PlatformExecutionPlan;
import java.util.*;

/** Converts a validated Composition graph at the single platform admission boundary. */
public final class CompositionPlatformExecutionPlanAdapter {
    private CompositionPlatformExecutionPlanAdapter() {}

    public static PlatformExecutionPlan adapt(TemplateWorkflow workflow,
            ProviderRegistryBoundary registry, Set<String> assets, Set<String> entitlements,
            PlatformExecutionPlan.Scope scope, ExecutionPlanId planId,
            String idempotencyKey, String requestHash,
            String quotaSnapshotId, long quotaUnits,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        if (entitlements != null) entitlements.forEach(e -> snapshot.put(e, "granted"));
        ExecutionMode requested = workflow.executionModes().size() == 1 ? workflow.executionModes().iterator().next() : null;
        return adapt(workflow, registry, assets, entitlements, scope, planId, idempotencyKey,
                requestHash, quotaSnapshotId, quotaUnits, snapshot, requested, audit);
    }

    public static PlatformExecutionPlan adapt(TemplateWorkflow workflow,
            ProviderRegistryBoundary registry, Set<String> assets, Set<String> entitlements,
            PlatformExecutionPlan.Scope scope, ExecutionPlanId planId,
            String idempotencyKey, String requestHash,
            String quotaSnapshotId, long quotaUnits, Map<String, String> entitlementSnapshot,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {
        return adapt(workflow, registry, assets, entitlements, scope, planId, idempotencyKey, requestHash,
                quotaSnapshotId, quotaUnits, entitlementSnapshot,
                workflow.executionModes().size() == 1 ? workflow.executionModes().iterator().next() : null, audit);
    }

    public static PlatformExecutionPlan adapt(TemplateWorkflow workflow,
            ProviderRegistryBoundary registry, Set<String> assets, Set<String> entitlements,
            PlatformExecutionPlan.Scope scope, ExecutionPlanId planId,
            String idempotencyKey, String requestHash, String quotaSnapshotId, long quotaUnits,
            Map<String, String> entitlementSnapshot, ExecutionMode requestedMode,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {
        Objects.requireNonNull(workflow); Objects.requireNonNull(registry);
        if (!workflow.tenantId().equals(scope.tenantId()) || !workflow.workspaceId().equals(scope.workspaceId()))
            throw new IllegalArgumentException("composition scope does not match authenticated scope");
        ValidationResult validation = CompositionValidator.validate(workflow, registry,
                assets == null ? Set.of() : assets, entitlements == null ? Set.of() : entitlements);
        if (!validation.ready()) throw new IllegalArgumentException("composition plan is not admissible: " + validation.issues());
        WorkflowEntry entry = Objects.requireNonNull(workflow.entry(), "validated workflow entry");
        WorkflowStep first = workflow.steps().stream().filter(s -> s.id().equals(entry.stepId())).findFirst().orElseThrow();
        CapabilityAvailability capability = registry.resolve(first.capabilityId(), first.capabilityVersion()).orElseThrow();
        List<PlatformExecutionPlan.TypedInputReference> inputs = new ArrayList<>();
        inputs.add(new PlatformExecutionPlan.TypedInputReference(entry.name(), entry.contract().name(), entry.contract().version(), "composition:" + workflow.id() + ":entry"));
        workflow.parameters().forEach(p -> inputs.add(new PlatformExecutionPlan.TypedInputReference("parameter:" + p.name(), p.type(), "1", String.valueOf(p.defaultValue()))));
        List<PlatformExecutionPlan.TypedOutputExpectation> outputs = workflow.outputs().stream()
                .map(o -> {
                    WorkflowStep outputStep = workflow.steps().stream().filter(s -> s.id().equals(o.stepId())).findFirst().orElseThrow();
                    CapabilityAvailability outputCapability = registry.resolve(outputStep.capabilityId(), outputStep.capabilityVersion()).orElseThrow();
                    return new PlatformExecutionPlan.TypedOutputExpectation(o.name(), o.type(), outputCapability.output().version(), "artifact");
                })
                .toList();
        if (requestedMode == null || !workflow.executionModes().contains(requestedMode)) throw new IllegalArgumentException("an explicit execution mode is required");
        PlatformExecutionPlan.ExecutionMode mode = PlatformExecutionPlan.ExecutionMode.valueOf(requestedMode.name());
        return new PlatformExecutionPlan(scope,
                new PlatformExecutionPlan.SourceRevision("composition", workflow.id(), workflow.version() + "@" + workflow.revision()),
                planId, new PlatformExecutionPlan.OperationIdentity(capability.capabilityId(), first.id()), inputs, outputs,
                mode, new PlatformExecutionPlan.IdempotencyIdentity(idempotencyKey, requestHash),
                new PlatformExecutionPlan.EntitlementQuotaSnapshot(quotaSnapshotId,
                        entitlementSnapshot == null ? Map.of() : entitlementSnapshot, quotaUnits), audit);
    }
}
