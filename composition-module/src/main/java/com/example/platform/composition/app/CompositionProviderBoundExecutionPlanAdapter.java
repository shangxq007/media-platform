package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import java.util.*;

/** Lowers only validated, published Composition data plus registry authority facts. */
public final class CompositionProviderBoundExecutionPlanAdapter {
    private CompositionProviderBoundExecutionPlanAdapter() {}

    public static ProviderBoundExecutionPlan lower(CompositionPublishedRevisionAuthority.PublishedRevision published,
            CompositionProviderBoundCapabilityAuthority authority,
            ProviderBoundExecutionPlan.Scope scope, Set<String> assets,
            ProviderBoundExecutionPlan.EntitlementQuotaSnapshot entitlementQuota,
            String idempotencyKey, String requestHash,
            String cancellationPolicy, String retryPolicy) {
        Objects.requireNonNull(published); Objects.requireNonNull(authority); Objects.requireNonNull(scope);
        Objects.requireNonNull(entitlementQuota, "entitlementQuota");
        TemplateWorkflow workflow = published.workflow();
        if (workflow.lifecycle() != Lifecycle.PUBLISHED) throw new IllegalArgumentException("composition revision is not published");
        if (!workflow.tenantId().equals(scope.tenantId()) || !workflow.workspaceId().equals(scope.workspaceId()))
            throw new IllegalArgumentException("composition scope does not match authenticated scope");
        Set<String> granted = new HashSet<>();
        entitlementQuota.entitlements().forEach((name, state) -> {
            if ("granted".equals(state)) granted.add(name);
        });
        ValidationResult validation = CompositionValidator.validate(workflow, authority,
                assets == null ? Set.of() : assets, granted);
        if (!validation.ready()) throw new IllegalArgumentException("composition plan is not admissible: " + validation.issues());
        if (entitlementQuota.quotaUnits() < workflow.estimate().quotaUnits().longValue())
            throw new IllegalArgumentException("quota snapshot is below the published Composition estimate");
        List<ProviderBoundExecutionPlan.CapabilityProviderBinding> bindings = new ArrayList<>();
        for (WorkflowStep step : workflow.steps()) {
            var resolved = authority.resolveProviderBound(step.capabilityId(), step.capabilityVersion())
                    .orElseThrow(() -> new IllegalArgumentException("provider-bound capability is unavailable: " + step.capabilityId()));
            var advertised = authority.resolve(step.capabilityId(), step.capabilityVersion()).orElseThrow();
            if (!resolved.capabilityId().equals(advertised.capabilityId())
                    || !resolved.capabilityVersion().equals(advertised.version())
                    || !resolved.inputContract().equals(advertised.input().name())
                    || !resolved.inputContractVersion().equals(advertised.input().version())
                    || !resolved.outputContract().equals(advertised.output().name())
                    || !resolved.outputContractVersion().equals(advertised.output().version()))
                throw new IllegalArgumentException("provider-bound contract differs from capability registry");
            bindings.add(new ProviderBoundExecutionPlan.CapabilityProviderBinding(step.id(),
                    resolved.capabilityId(), resolved.capabilityVersion(),
                    new ProviderBoundExecutionPlan.ProviderIdentity(resolved.providerRegistryReference()),
                    resolved.providerContractVersion(), resolved.inputContract(), resolved.inputContractVersion(),
                    resolved.outputContract(), resolved.outputContractVersion()));
        }
        WorkflowEntry entry = Objects.requireNonNull(workflow.entry(), "validated workflow entry");
        List<ProviderBoundExecutionPlan.TypedReference> inputs = new ArrayList<>();
        inputs.add(new ProviderBoundExecutionPlan.TypedReference(entry.name(), entry.contract().name(),
                entry.contract().version(), "composition:" + workflow.id() + ":entry"));
        workflow.parameters().forEach(p -> inputs.add(new ProviderBoundExecutionPlan.TypedReference(
                "parameter:" + p.name(), p.type(), "1", String.valueOf(p.defaultValue()))));
        List<ProviderBoundExecutionPlan.TypedOutputContract> outputs = workflow.outputs().stream()
                .map(o -> new ProviderBoundExecutionPlan.TypedOutputContract(o.name(), o.type(),
                        outputVersion(workflow, o, authority), "typed-output"))
                .toList();
        if (workflow.executionModes().size() != 1) throw new IllegalArgumentException("exactly one execution mode is required");
        return new ProviderBoundExecutionPlan(
                new ProviderBoundExecutionPlan.PublishedRevision("composition", workflow.id(), workflow.version(), workflow.revision()),
                scope, bindings, inputs, outputs,
                ProviderBoundExecutionPlan.ExecutionMode.valueOf(workflow.executionModes().iterator().next().name()),
                entitlementQuota,
                new ProviderBoundExecutionPlan.PlanFingerprint(published.planFingerprint()),
                new ProviderBoundExecutionPlan.IdempotencyIdentity(idempotencyKey, requestHash),
                new ProviderBoundExecutionPlan.PolicyReferences(cancellationPolicy, retryPolicy));
    }

    private static String outputVersion(TemplateWorkflow workflow, WorkflowOutput output,
            CompositionProviderBoundCapabilityAuthority authority) {
        WorkflowStep step = workflow.steps().stream().filter(s -> s.id().equals(output.stepId())).findFirst().orElseThrow();
        return authority.resolveProviderBound(step.capabilityId(), step.capabilityVersion()).orElseThrow().outputContractVersion();
    }

}
