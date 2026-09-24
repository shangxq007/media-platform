package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
import com.example.platform.composition.domain.CompositionModels.EntitlementRequirement;
import com.example.platform.entitlement.api.EntitlementDecisionQuery;
import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/** Resolves entitlement state and remaining quota from the canonical entitlement authority. */
@Component
public final class CanonicalCompositionEntitlementQuotaResolver implements CompositionEntitlementQuotaResolver {
    private final EntitlementDecisionQuery decisions;
    public CanonicalCompositionEntitlementQuotaResolver(EntitlementDecisionQuery decisions) { this.decisions = Objects.requireNonNull(decisions); }

    @Override public ProviderBoundExecutionPlan.EntitlementQuotaSnapshot resolve(TemplateWorkflow workflow, String tenantId, String workspaceId, String actorId) {
        if (!Objects.equals(workflow.tenantId(), tenantId) || !Objects.equals(workflow.workspaceId(), workspaceId))
            throw new IllegalArgumentException("entitlement scope does not match authenticated scope");
        BigDecimal estimate = com.example.platform.entitlement.domain.QuotaQuantity.exact(workflow.estimate().quotaUnits(), "published estimate");
        if (estimate == null || estimate.signum() <= 0) throw new IllegalArgumentException("published Composition estimate must be positive");
        String quotaKey = "composition";
        java.time.Instant periodStart = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        java.time.Instant periodEnd = periodStart.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant();
        LinkedHashMap<String, EntitlementRequirement> required = new LinkedHashMap<>();
        workflow.entitlements().forEach(r -> addRequirement(required, r));
        workflow.steps().forEach(step -> step.entitlements().forEach(r -> addRequirement(required, r)));
        if (required.isEmpty()) required.put("composition", new EntitlementRequirement("composition", "1"));
        Set<String> identities = new HashSet<>();
        List<ProviderBoundExecutionPlan.EntitlementFact> facts = new ArrayList<>();
        BigDecimal remaining = null;
        Map<String,String> granted = new LinkedHashMap<>();
        for (EntitlementRequirement req : required.values()) {
            if (!identities.add(req.key())) throw new IllegalArgumentException("duplicate entitlement identity");
            var requirement = new AccessCheckRequest.VersionedRequirement(req.key(), req.version(), quotaKey, periodStart, periodEnd);
            var decision = decisions.evaluate(new AccessCheckRequest(tenantId, workspaceId, actorId, "USER", actorId,
                    "composition.admit", "COMPOSITION", workflow.id(), req.key(), null, null, "WEB", null, Map.of(), requirement));
            if (!decision.allowed() || decision.quotaRemaining() == null || decision.expiresAt() != null && !decision.expiresAt().isAfter(java.time.Instant.now()))
                throw new IllegalArgumentException("entitlement is unavailable: " + req.key() + "@" + req.version());
            if (decision.matchedGrantId() == null || decision.authoritativeGrant() == null) throw new IllegalArgumentException("published entitlement has no versioned grant");
            facts.add(new ProviderBoundExecutionPlan.EntitlementFact(req.key(), req.version(), decision.matchedGrantId(), decision.authoritativeGrant().version()));
            granted.put(req.key() + "@" + req.version(), "granted:" + decision.matchedGrantId());
            if (remaining != null && remaining.compareTo(decision.quotaRemaining()) != 0)
                throw new IllegalArgumentException("CONFLICTING_QUOTA_FACTS");
            remaining = decision.quotaRemaining();
        }
        if (remaining == null || estimate.compareTo(remaining) > 0) throw new IllegalArgumentException("published Composition estimate exceeds remaining quota");
        String snapshot = facts.stream().map(f -> f.identity() + "@" + f.requiredVersion() + ":" + f.grantId()).sorted().reduce("quota:" + quotaKey + ":" + periodStart, (a,b) -> a + "|" + b);
        return new ProviderBoundExecutionPlan.EntitlementQuotaSnapshot(snapshot, facts, estimate, remaining, periodStart, periodEnd, quotaKey);
    }

    private static void addRequirement(Map<String, EntitlementRequirement> required, EntitlementRequirement req) {
        EntitlementRequirement previous = required.putIfAbsent(req.key(), req);
        if (previous != null && !previous.version().equals(req.version())) throw new IllegalArgumentException("conflicting entitlement versions for " + req.key());
    }

}
