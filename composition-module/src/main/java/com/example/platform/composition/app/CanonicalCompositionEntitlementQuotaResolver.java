package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.TemplateWorkflow;
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
        Map<String, String> granted = new LinkedHashMap<>();
        BigDecimal remaining = null; String provenance = null;
        for (String entitlement : java.util.Set.of("composition")) {
            var decision = decisions.evaluate(new AccessCheckRequest(tenantId, workspaceId, actorId, "USER", actorId,
                    "composition.admit", "COMPOSITION", workflow.id(), entitlement, null, null, "WEB", null, Map.of()));
            if (!decision.allowed() || decision.expiresAt() != null && !decision.expiresAt().isAfter(java.time.Instant.now()))
                throw new IllegalArgumentException("entitlement is unavailable: " + entitlement);
            granted.put(entitlement, "granted"); provenance = decision.matchedGrantId();
            if (decision.quotaRemaining() != null) remaining = BigDecimal.valueOf(decision.quotaRemaining());
        }
        if (remaining == null) throw new IllegalArgumentException("quota authority did not return availability");
        String snapshot = (provenance == null ? "quota" : provenance) + ":" + remaining.toPlainString();
        return new ProviderBoundExecutionPlan.EntitlementQuotaSnapshot(snapshot, granted, remaining);
    }
}
