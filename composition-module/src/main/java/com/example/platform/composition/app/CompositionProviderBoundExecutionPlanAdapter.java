package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
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
        entitlementQuota.entitlements().forEach((name, state) -> {
            if (state != null && state.startsWith("granted")) granted.add(name.split("@", 2)[0]);
        });
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
                .map(ref -> new ProviderBoundExecutionPlan.TypedReference(ref, "MediaAsset", "1", "composition:asset:" + ref))
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
                        canonicalIntentHash(published, scope, parameters, entitlementQuota, cancellationPolicy, retryPolicy)),
                new ProviderBoundExecutionPlan.PolicyReferences(cancellationPolicy, retryPolicy));
    }

    private static String canonicalIntentHash(CompositionPublishedRevisionAuthority.PublishedRevision published,
            ProviderBoundExecutionPlan.Scope scope, Map<String, Object> parameters,
            ProviderBoundExecutionPlan.EntitlementQuotaSnapshot quota, String cancellationPolicy, String retryPolicy) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            canonical.append("composition-admission-v2\n").append(scope.tenantId()).append('\n')
                    .append(scope.workspaceId()).append('\n').append(scope.actorId()).append('\n')
                    .append(published.workflow().id()).append('\n').append(published.workflow().version()).append('\n')
                    .append(published.workflow().revision()).append('\n').append(published.planFingerprint()).append('\n')
                    .append(cancellationPolicy).append('\n').append(retryPolicy).append('\n')
                    .append(published.workflow().executionModes()).append('\n')
                    .append(published.workflow().estimate().quotaUnits().toPlainString()).append('\n')
                    .append(quota.entitlements()).append('\n');
            new java.util.TreeMap<>(parameters == null ? Map.of() : parameters).forEach((k,v) -> canonical.append(k).append('=').append(String.valueOf(v)).append('\n'));
            published.workflow().requiredAssets().stream().sorted().forEach(a -> canonical.append("asset=").append(a).append('\n'));
            published.workflow().steps().forEach(step -> {
                canonical.append("step=").append(step.id()).append(':').append(step.capabilityId()).append('@').append(step.capabilityVersion()).append('\n');
                step.requiredAssets().stream().sorted().forEach(a -> canonical.append("stepAsset=").append(step.id()).append(':').append(a).append('\n'));
                step.entitlements().stream().sorted(java.util.Comparator.comparing(EntitlementRequirement::key).thenComparing(EntitlementRequirement::version)).forEach(e -> canonical.append("entitlement=").append(e.key()).append('@').append(e.version()).append('\n'));
            });
            return java.util.HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static String outputVersion(TemplateWorkflow workflow, WorkflowOutput output,
            CompositionProviderBoundCapabilityAuthority authority) {
        WorkflowStep step = workflow.steps().stream().filter(s -> s.id().equals(output.stepId())).findFirst().orElseThrow();
        return authority.resolveProviderBound(step.capabilityId(), step.capabilityVersion()).orElseThrow().outputContractVersion();
    }

}
