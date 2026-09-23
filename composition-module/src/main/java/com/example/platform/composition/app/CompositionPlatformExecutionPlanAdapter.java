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
                .map(o -> new PlatformExecutionPlan.TypedOutputExpectation(o.name(), o.type(), capability.output().version(), "artifact"))
                .toList();
        String modeName = workflow.executionModes().stream().map(ExecutionMode::name).sorted().findFirst().orElseThrow();
        PlatformExecutionPlan.ExecutionMode mode = PlatformExecutionPlan.ExecutionMode.valueOf(modeName);
        return new PlatformExecutionPlan(scope,
                new PlatformExecutionPlan.SourceRevision("composition", workflow.id(), workflow.version() + "@" + workflow.revision()),
                planId, new PlatformExecutionPlan.OperationIdentity(capability.capabilityId(), first.id()), inputs, outputs,
                mode, new PlatformExecutionPlan.IdempotencyIdentity(idempotencyKey, requestHash),
                new PlatformExecutionPlan.EntitlementQuotaSnapshot(quotaSnapshotId, Map.of(), quotaUnits), audit);
    }
}
