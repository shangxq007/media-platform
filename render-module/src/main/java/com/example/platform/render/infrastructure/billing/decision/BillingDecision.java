package com.example.platform.render.infrastructure.billing.decision;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable billing decision output from the BillingDecisionEngine.
 * 
 * <p>This is a pure decision - it does not mutate state.
 * The caller is responsible for acting on the decision.
 */
public record BillingDecision(
        DecisionType decision,
        ReasonCode reasonCode,
        String reasonMessage,
        CostEstimate costEstimate,
        QuotaImpact quotaImpact,
        Long retryAfterMs,
        String traceId,
        Instant decidedAt,
        Map<String, Object> metadata
) {
    /**
     * Create an ALLOW decision.
     */
    public static BillingDecision allow(CostEstimate costEstimate, QuotaImpact quotaImpact, String traceId) {
        return new BillingDecision(
                DecisionType.ALLOW,
                ReasonCode.OK,
                "Billing checks passed",
                costEstimate,
                quotaImpact,
                null,
                traceId,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a DENY decision.
     */
    public static BillingDecision deny(ReasonCode reasonCode, String reasonMessage, String traceId) {
        return new BillingDecision(
                DecisionType.DENY,
                reasonCode,
                reasonMessage,
                null,
                null,
                null,
                traceId,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a THROTTLE decision.
     */
    public static BillingDecision throttle(ReasonCode reasonCode, String reasonMessage, 
                                            long retryAfterMs, String traceId) {
        return new BillingDecision(
                DecisionType.THROTTLE,
                reasonCode,
                reasonMessage,
                null,
                null,
                retryAfterMs,
                traceId,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Create a REQUIRE_CREDITS decision.
     */
    public static BillingDecision requireCredits(CostEstimate costEstimate, double currentBalance,
                                                  String traceId) {
        return new BillingDecision(
                DecisionType.REQUIRE_CREDITS,
                ReasonCode.INSUFFICIENT_CREDITS,
                String.format("Insufficient credits: need %.2f, have %.2f", 
                        costEstimate.estimatedCost(), currentBalance),
                costEstimate,
                null,
                null,
                traceId,
                Instant.now(),
                Map.of("currentBalance", currentBalance)
        );
    }

    /**
     * Create a REQUIRE_UPGRADE decision.
     */
    public static BillingDecision requireUpgrade(ReasonCode reasonCode, String reasonMessage,
                                                  String currentTier, String requiredTier,
                                                  String traceId) {
        return new BillingDecision(
                DecisionType.REQUIRE_UPGRADE,
                reasonCode,
                reasonMessage,
                null,
                null,
                null,
                traceId,
                Instant.now(),
                Map.of("currentTier", currentTier, "requiredTier", requiredTier)
        );
    }

    /**
     * Check if the decision allows execution.
     */
    public boolean isAllowed() {
        return decision == DecisionType.ALLOW;
    }

    /**
     * Check if the decision denies execution.
     */
    public boolean isDenied() {
        return decision == DecisionType.DENY;
    }

    /**
     * Check if the decision requires credits.
     */
    public boolean requiresCredits() {
        return decision == DecisionType.REQUIRE_CREDITS;
    }

    /**
     * Check if the decision requires upgrade.
     */
    public boolean requiresUpgrade() {
        return decision == DecisionType.REQUIRE_UPGRADE;
    }

    /**
     * Get a human-readable summary.
     */
    public String getSummary() {
        return String.format("[%s] %s: %s", decision, reasonCode, reasonMessage);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public enum DecisionType {
        ALLOW,
        DENY,
        THROTTLE,
        REQUIRE_CREDITS,
        REQUIRE_UPGRADE
    }

    public enum ReasonCode {
        // Success
        OK,
        
        // Subscription issues
        NO_SUBSCRIPTION,
        SUBSCRIPTION_INACTIVE,
        SUBSCRIPTION_EXPIRED,
        
        // Quota issues
        QUOTA_EXCEEDED,
        QUOTA_BUCKET_FULL,
        QUOTA_RATE_LIMITED,
        
        // Credit issues
        INSUFFICIENT_CREDITS,
        CREDIT_LIMIT_REACHED,
        
        // Tier issues
        TIER_NOT_ALLOWED,
        FEATURE_NOT_AVAILABLE,
        PROVIDER_NOT_AVAILABLE,
        
        // Resource issues
        RESOURCE_EXHAUSTED,
        CONCURRENT_LIMIT_EXCEEDED,
        
        // Policy issues
        POLICY_VIOLATION,
        GEO_RESTRICTED,
        
        // System issues
        SYSTEM_ERROR,
        SERVICE_UNAVAILABLE
    }

    /**
     * Cost estimate for the operation.
     */
    public record CostEstimate(
            double estimatedCost,
            String currency,
            String providerKey,
            String preset,
            long estimatedDurationSeconds,
            boolean useGpu,
            Map<String, Double> costBreakdown
    ) {
        public static CostEstimate of(double cost, String currency) {
            return new CostEstimate(cost, currency, null, null, 0, false, Map.of());
        }
    }

    /**
     * Impact on quota if operation proceeds.
     */
    public record QuotaImpact(
            String quotaKey,
            long currentUsage,
            long limit,
            long additionalUsage,
            double resultingUtilization
    ) {
        public static QuotaImpact of(String quotaKey, long current, long limit, long additional) {
            double utilization = limit > 0 ? (double)(current + additional) / limit : 0;
            return new QuotaImpact(quotaKey, current, limit, additional, utilization);
        }

        public boolean wouldExceed() {
            return currentUsage + additionalUsage > limit;
        }
    }
}
