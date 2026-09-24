package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.admission.ProviderBoundExecutionPlan;
import java.util.*;

/** Lowers only validated, published Composition data plus registry authority facts. */
public final class CompositionProviderBoundExecutionPlanAdapter {
    private CompositionProviderBoundExecutionPlanAdapter() {}

    public static ProviderBoundExecutionPlan lower(CompositionPublishedRevisionAuthority revisions,
            CompositionProviderBoundCapabilityAuthority authority,
            CompositionResourceResolver resources, CompositionEntitlementQuotaResolver entitlements,
            ProviderBoundExecutionPlan.Scope scope, String compositionId, String version,
            String idempotencyKey, String requestHash,
            String cancellationPolicy, String retryPolicy) {
        return lower(revisions, authority, resources, entitlements, scope, compositionId, version,
                idempotencyKey, requestHash, cancellationPolicy, retryPolicy, Map.of());
    }

    public static ProviderBoundExecutionPlan lower(CompositionPublishedRevisionAuthority revisions,
            CompositionProviderBoundCapabilityAuthority authority,
            CompositionResourceResolver resources, CompositionEntitlementQuotaResolver entitlements,
            ProviderBoundExecutionPlan.Scope scope, String compositionId, String version,
            String idempotencyKey, String requestHash,
            String cancellationPolicy, String retryPolicy, Map<String, Object> parameters) {
        Objects.requireNonNull(revisions); Objects.requireNonNull(authority); Objects.requireNonNull(resources);
        Objects.requireNonNull(entitlements); Objects.requireNonNull(scope);
        CompositionPublishedRevisionAuthority.PublishedRevision published = revisions.resolve(
                scope.tenantId(), scope.workspaceId(), compositionId, version)
                .orElseThrow(() -> new IllegalArgumentException("published Composition revision is unavailable"));
        TemplateWorkflow workflow = published.workflow();
        if (!workflow.id().equals(compositionId) || !workflow.version().equals(version))
            throw new IllegalArgumentException("resolved revision identity does not match requested identity");
        if (workflow.lifecycle() != Lifecycle.PUBLISHED) throw new IllegalArgumentException("composition revision is not published");
        if (!workflow.tenantId().equals(scope.tenantId()) || !workflow.workspaceId().equals(scope.workspaceId()))
            throw new IllegalArgumentException("composition scope does not match authenticated scope");
        CompositionValidator.validateParameters(workflow, parameters == null ? Map.of() : parameters);
        var resourceResolution = resources.resolve(workflow, scope.tenantId(), scope.workspaceId());
        var entitlementQuota = entitlements.resolve(workflow, scope.tenantId(), scope.workspaceId(), scope.actorId());
        if (entitlementQuota == null) throw new IllegalArgumentException("entitlement/quota decision is unavailable");
        Set<String> granted = new HashSet<>();
        if (!entitlementQuota.entitlementFacts().isEmpty()) {
            entitlementQuota.entitlementFacts().forEach(f -> granted.add(f.identity() + "@" + f.requiredVersion()));
        } else {
            entitlementQuota.entitlements().forEach((name, state) -> {
                if (state != null && state.startsWith("granted")) granted.add(name);
            });
        }
        ValidationResult validation = CompositionValidator.validate(workflow, authority,
                resourceResolution.availableAssets(), granted);
        if (!validation.ready()) throw new IllegalArgumentException("composition plan is not admissible: " + validation.issues());
        if (workflow.estimate().quotaUnits() == null || workflow.estimate().quotaUnits().signum() <= 0)
            throw new IllegalArgumentException("published Composition estimate must be positive");
        if (entitlementQuota.quotaRemaining().compareTo(workflow.estimate().quotaUnits()) < 0)
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
        List<ProviderBoundExecutionPlan.TypedReference> resourceRefs = resourceResolution.references().stream()
                .map(ref -> new ProviderBoundExecutionPlan.TypedReference(ref, "Artifact", "1", "composition:asset:" + ref))
                .toList();
        List<ProviderBoundExecutionPlan.TypedOutputContract> outputs = workflow.outputs().stream()
                .map(o -> new ProviderBoundExecutionPlan.TypedOutputContract(o.name(), o.type(),
                        outputVersion(workflow, o, authority), "typed-output"))
                .toList();
        if (workflow.executionModes().size() != 1) throw new IllegalArgumentException("exactly one execution mode is required");
        return new ProviderBoundExecutionPlan(
                new ProviderBoundExecutionPlan.PublishedRevision("composition", workflow.id(), workflow.version(), workflow.revision()),
                scope, bindings, inputs, resourceRefs, outputs,
                ProviderBoundExecutionPlan.ExecutionMode.valueOf(workflow.executionModes().iterator().next().name()),
                entitlementQuota,
                new ProviderBoundExecutionPlan.PlanFingerprint(published.planFingerprint()),
                new ProviderBoundExecutionPlan.IdempotencyIdentity(idempotencyKey,
                        canonicalIntentHash(published, scope, parameters, entitlementQuota, cancellationPolicy, retryPolicy, bindings, resourceRefs, inputs, outputs)),
                new ProviderBoundExecutionPlan.PolicyReferences(cancellationPolicy, retryPolicy));
    }

    private static String canonicalIntentHash(CompositionPublishedRevisionAuthority.PublishedRevision published,
            ProviderBoundExecutionPlan.Scope scope, Map<String, Object> parameters,
            ProviderBoundExecutionPlan.EntitlementQuotaSnapshot quota, String cancellationPolicy, String retryPolicy,
            List<ProviderBoundExecutionPlan.CapabilityProviderBinding> bindings,
            List<ProviderBoundExecutionPlan.TypedReference> resourceRefs,
            List<ProviderBoundExecutionPlan.TypedReference> inputs,
            List<ProviderBoundExecutionPlan.TypedOutputContract> outputs) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            java.util.function.BiConsumer<String,String> field = (k,v) -> {
                String key = CompositionIntentEncoding.text(k);
                String value = CompositionIntentEncoding.text(v);
                canonical.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value);
            };
            field.accept("scope.tenant", scope.tenantId()); field.accept("scope.workspace", scope.workspaceId()); field.accept("scope.actor", scope.actorId());
            field.accept("composition.id", published.workflow().id()); field.accept("composition.version", published.workflow().version());
            field.accept("composition.revision", Long.toString(published.workflow().revision())); field.accept("composition.fingerprint", published.planFingerprint());
            field.accept("estimate", published.workflow().estimate().quotaUnits().toPlainString()); field.accept("quota.key", quota.quotaKey());
            field.accept("quota.start", String.valueOf(quota.quotaPeriodStart())); field.accept("quota.end", String.valueOf(quota.quotaPeriodEnd()));
            field.accept("execution.mode", published.workflow().executionModes().stream().map(Enum::name).sorted().reduce("", (a,b) -> a + ":" + b));
            field.accept("policy.cancel", cancellationPolicy); field.accept("policy.retry", retryPolicy);
            quota.entitlementFacts().stream().sorted(java.util.Comparator.comparing(ProviderBoundExecutionPlan.EntitlementFact::identity).thenComparing(ProviderBoundExecutionPlan.EntitlementFact::requiredVersion)).forEach(f -> { field.accept("entitlement.identity", f.identity()); field.accept("entitlement.requiredVersion", f.requiredVersion()); field.accept("entitlement.grant", f.grantId()); field.accept("entitlement.authoritativeVersion", Long.toString(f.authoritativeVersion())); });
            published.workflow().parameters().stream().sorted(java.util.Comparator.comparing(Parameter::name)).forEach(p -> { field.accept("parameter.definition.name", p.name()); field.accept("parameter.definition.type", p.type()); field.accept("parameter.definition.required", Boolean.toString(p.required())); field.accept("parameter.definition.minimum", String.valueOf(p.minimum())); field.accept("parameter.definition.maximum", String.valueOf(p.maximum())); appendStructured(field, "parameter.definition.default", p.defaultValue()); });
            parameters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> { field.accept("parameter.name", e.getKey()); appendStructured(field, "parameter.value", e.getValue()); });
            bindings.stream().sorted(java.util.Comparator.comparing(ProviderBoundExecutionPlan.CapabilityProviderBinding::nodeId)).forEach(b -> { field.accept("binding.node", b.nodeId()); field.accept("binding.capability", b.capabilityId()); field.accept("binding.capabilityVersion", b.capabilityVersion()); field.accept("binding.provider", b.providerIdentity().registryReference()); field.accept("binding.providerContract", b.providerContractVersion()); field.accept("binding.input", b.inputContract()); field.accept("binding.output", b.outputContract()); field.accept("binding.inputVersion", b.inputContractVersion()); field.accept("binding.outputVersion", b.outputContractVersion()); });
            resourceRefs.stream().sorted(java.util.Comparator.comparing(ProviderBoundExecutionPlan.TypedReference::reference)).forEach(r -> { field.accept("resource.name", r.name()); field.accept("resource.contract", r.contract()); field.accept("resource.version", r.contractVersion()); field.accept("resource.reference", r.reference()); });
            inputs.forEach(r -> { field.accept("input.name", r.name()); field.accept("input.contract", r.contract()); field.accept("input.version", r.contractVersion()); field.accept("input.reference", r.reference()); });
            outputs.forEach(o -> { field.accept("output.name", o.name()); field.accept("output.contract", o.contract()); field.accept("output.version", o.contractVersion()); field.accept("output.kind", o.materializationKind()); });
            return java.util.HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static void appendStructured(java.util.function.BiConsumer<String,String> field, String key, Object value) {
        field.accept(key, CompositionIntentEncoding.text(value));
    }

    private static String outputVersion(TemplateWorkflow workflow, WorkflowOutput output,
            CompositionProviderBoundCapabilityAuthority authority) {
        WorkflowStep step = workflow.steps().stream().filter(s -> s.id().equals(output.stepId())).findFirst().orElseThrow();
        return authority.resolveProviderBound(step.capabilityId(), step.capabilityVersion()).orElseThrow().outputContractVersion();
    }

}
