package com.example.platform.render.infrastructure.billing.decision;

import java.time.Instant;
import java.util.Map;

/**
 * Trace node for billing decisions.
 * Integrates with the observability system.
 */
public record BillingDecisionTraceNode(
        String traceId,
        String tenantId,
        String actionType,
        String decision,
        String reasonCode,
        String reasonMessage,
        Double estimatedCost,
        String providerCandidate,
        Long durationMs,
        Instant timestamp,
        Map<String, Object> metadata
) {
    /**
     * Create a trace node from a billing decision.
     */
    public static BillingDecisionTraceNode fromDecision(BillingDecision decision, 
                                                          BillingDecisionRequest request,
                                                          long durationMs) {
        return new BillingDecisionTraceNode(
                decision.traceId(),
                request.tenantId(),
                request.actionType().name(),
                decision.decision().name(),
                decision.reasonCode().name(),
                decision.reasonMessage(),
                decision.costEstimate() != null ? decision.costEstimate().estimatedCost() : null,
                request.providerCandidate(),
                durationMs,
                Instant.now(),
                Map.of(
                        "dryRun", request.dryRun(),
                        "userId", request.userId() != null ? request.userId() : "unknown",
                        "workspaceId", request.workspaceId() != null ? request.workspaceId() : "unknown"
                )
        );
    }

    /**
     * Get a human-readable description.
     */
    public String getDescription() {
        return String.format("[%s] %s %s: %s (%s) - %s",
                traceId != null ? traceId.substring(0, Math.min(8, traceId.length())) : "no-trace",
                actionType,
                decision,
                reasonCode,
                reasonMessage,
                estimatedCost != null ? String.format("$%.4f", estimatedCost) : "no cost");
    }

    /**
     * Check if this was a successful decision.
     */
    public boolean isAllowed() {
        return "ALLOW".equals(decision);
    }

    /**
     * Check if this was a dry run.
     */
    public boolean isDryRun() {
        return metadata.containsKey("dryRun") && Boolean.TRUE.equals(metadata.get("dryRun"));
    }
}
