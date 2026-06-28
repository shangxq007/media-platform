package com.example.platform.render.domain.timeline.compile.binding;

import java.util.List;

/**
 * Decision record for provider binding on a single capability node.
 *
 * <p>Internal only — captures the full binding decision context
 * including candidates, selection, and failure reasons.</p>
 *
 * @param nodeId          capability graph node ID
 * @param artifactType    artifact node type
 * @param requiredCapabilities capabilities required by this node
 * @param status          binding status
 * @param selectedProvider bound provider (null if unbound)
 * @param candidates      all candidate providers considered
 * @param failureReason   reason for failure (null if bound)
 * @param explanation     human-readable explanation of the decision
 */
public record ProviderBindingDecision(
        String nodeId,
        String artifactType,
        List<String> requiredCapabilities,
        ProviderBindingStatus status,
        BoundProviderRef selectedProvider,
        List<BoundProviderRef> candidates,
        ProviderBindingFailureReason failureReason,
        String explanation) {

    /**
     * Returns true if this decision resulted in a successful binding.
     */
    public boolean isBound() {
        return status == ProviderBindingStatus.BOUND && selectedProvider != null;
    }

    /**
     * Returns true if this decision failed.
     */
    public boolean isFailed() {
        return status != ProviderBindingStatus.BOUND;
    }

    /**
     * Creates a successful binding decision.
     */
    public static ProviderBindingDecision bound(
            String nodeId, String artifactType, List<String> requiredCapabilities,
            BoundProviderRef selectedProvider, List<BoundProviderRef> candidates,
            String explanation) {
        return new ProviderBindingDecision(
                nodeId, artifactType, requiredCapabilities,
                ProviderBindingStatus.BOUND, selectedProvider, candidates,
                null, explanation);
    }

    /**
     * Creates a failed binding decision.
     */
    public static ProviderBindingDecision failed(
            String nodeId, String artifactType, List<String> requiredCapabilities,
            ProviderBindingStatus status, ProviderBindingFailureReason reason,
            List<BoundProviderRef> candidates, String explanation) {
        return new ProviderBindingDecision(
                nodeId, artifactType, requiredCapabilities,
                status, null, candidates, reason, explanation);
    }
}
