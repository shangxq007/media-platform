package com.example.platform.render.infrastructure.billing.decision;

import java.time.Instant;
import java.util.Map;

/**
 * Input request for the BillingDecisionEngine.
 * Contains all context needed to make a billing decision.
 */
public record BillingDecisionRequest(
        String tenantId,
        String userId,
        String workspaceId,
        ActionType actionType,
        double estimatedCost,
        ResourceProfile resourceProfile,
        String providerCandidate,
        String preset,
        Map<String, Long> currentUsage,
        SubscriptionState subscriptionState,
        QuotaState quotaState,
        double creditBalance,
        boolean dryRun,
        String traceId
) {
    /**
     * Create a request for render job creation.
     */
    public static BillingDecisionRequest forRenderJobCreate(
            String tenantId, String userId, String workspaceId,
            String providerCandidate, String preset,
            long estimatedDurationSeconds, boolean useGpu) {
        return new BillingDecisionRequest(
                tenantId,
                userId,
                workspaceId,
                ActionType.RENDER_JOB_CREATE,
                0, // Will be estimated by engine
                new ResourceProfile(estimatedDurationSeconds, useGpu, 0, 0),
                providerCandidate,
                preset,
                Map.of(),
                null,
                null,
                0,
                false,
                null
        );
    }

    /**
     * Create a request for render job execution.
     */
    public static BillingDecisionRequest forRenderJobExecute(
            String tenantId, String userId, String workspaceId,
            String providerCandidate, String preset,
            long estimatedDurationSeconds, boolean useGpu, double estimatedCost) {
        return new BillingDecisionRequest(
                tenantId,
                userId,
                workspaceId,
                ActionType.EXECUTE,
                estimatedCost,
                new ResourceProfile(estimatedDurationSeconds, useGpu, 0, 0),
                providerCandidate,
                preset,
                Map.of(),
                null,
                null,
                0,
                false,
                null
        );
    }

    /**
     * Create a dry-run request for cost preview.
     */
    public static BillingDecisionRequest forCostPreview(
            String tenantId, String workspaceId,
            String providerCandidate, String preset,
            long estimatedDurationSeconds, boolean useGpu) {
        return new BillingDecisionRequest(
                tenantId,
                null,
                workspaceId,
                ActionType.RENDER_JOB_CREATE,
                0,
                new ResourceProfile(estimatedDurationSeconds, useGpu, 0, 0),
                providerCandidate,
                preset,
                Map.of(),
                null,
                null,
                0,
                true, // dry run
                null
        );
    }

    /**
     * Action types that can be gated by billing.
     */
    public enum ActionType {
        RENDER_JOB_CREATE,
        EXECUTE,
        PREVIEW,
        TEMPLATE_CREATE,
        ASSET_UPLOAD
    }

    /**
     * Resource profile for cost estimation.
     */
    public record ResourceProfile(
            long estimatedDurationSeconds,
            boolean useGpu,
            long outputSizeBytes,
            int concurrentJobs
    ) {}

    /**
     * Current subscription state (cached or fetched).
     */
    public record SubscriptionState(
            boolean hasActiveSubscription,
            String planKey,
            String tier,
            Instant periodEndAt,
            Map<String, Long> includedQuota
    ) {}

    /**
     * Current quota state (cached or fetched).
     */
    public record QuotaState(
            Map<String, Long> currentUsage,
            Map<String, Long> limits,
            Map<String, Double> utilization
    ) {
        /**
         * Check if a specific quota would be exceeded.
         */
        public boolean wouldExceed(String quotaKey, long additionalUsage) {
            Long current = currentUsage.getOrDefault(quotaKey, 0L);
            Long limit = limits.getOrDefault(quotaKey, Long.MAX_VALUE);
            return current + additionalUsage > limit;
        }

        /**
         * Get utilization for a specific quota.
         */
        public double getUtilization(String quotaKey) {
            return utilization.getOrDefault(quotaKey, 0.0);
        }
    }
}
