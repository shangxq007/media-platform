package com.example.platform.render.infrastructure.billing.policy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Trace node for policy decisions.
 * Integrates with the observability system.
 */
public record PolicyDecisionTraceNode(
        String traceId,
        String tenantId,
        String actionType,
        boolean allowed,
        String denyReason,
        List<String> appliedPolicyIds,
        List<String> appliedActions,
        double discountPercent,
        double multiplier,
        double finalPrice,
        Instant timestamp,
        Map<String, Object> metadata
) {
    /**
     * Create a trace node from policy evaluation result.
     */
    public static PolicyDecisionTraceNode fromEvaluation(
            String traceId,
            String tenantId,
            String actionType,
            PolicyEngine.PolicyEvaluationResult evaluation,
            PricingEngine.PricingResult pricingResult) {
        return new PolicyDecisionTraceNode(
                traceId,
                tenantId,
                actionType,
                evaluation.isAllowed(),
                evaluation.denyReason(),
                evaluation.appliedPolicyIds(),
                evaluation.appliedActions().stream()
                        .map(a -> a.type().name())
                        .toList(),
                evaluation.totalDiscountPercent(),
                evaluation.totalMultiplier(),
                pricingResult != null ? pricingResult.finalPrice() : 0,
                Instant.now(),
                Map.of(
                        "policiesEvaluated", evaluation.appliedPolicyIds().size(),
                        "actionsApplied", evaluation.appliedActions().size()
                )
        );
    }

    /**
     * Get a human-readable description.
     */
    public String getDescription() {
        if (!allowed) {
            return String.format("[%s] DENIED: %s (policies: %s)",
                    traceId, denyReason, appliedPolicyIds);
        }
        return String.format("[%s] ALLOWED (policies: %s, discount: %.1f%%, final: $%.4f)",
                traceId, appliedPolicyIds, discountPercent, finalPrice);
    }
}
