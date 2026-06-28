package com.example.platform.render.domain.timeline.compile.binding;

import com.example.platform.render.domain.timeline.compile.ArtifactNodeType;
import java.util.List;

/**
 * A node in the provider binding plan.
 *
 * <p>Maps a capability graph node to its provider binding decision.</p>
 *
 * @param nodeId           capability graph node ID
 * @param artifactNodeType type of artifact node
 * @param label            human-readable label
 * @param requiredCapabilities required capability codes
 * @param decision         the binding decision for this node
 */
public record ProviderBindingNode(
        String nodeId,
        ArtifactNodeType artifactNodeType,
        String label,
        List<String> requiredCapabilities,
        ProviderBindingDecision decision) {

    /**
     * Returns true if this node is bound to a provider.
     */
    public boolean isBound() {
        return decision != null && decision.isBound();
    }

    /**
     * Returns true if this node failed binding.
     */
    public boolean isFailed() {
        return decision == null || decision.isFailed();
    }

    /**
     * Returns the bound provider name, or null if unbound.
     */
    public String boundProviderName() {
        return isBound() ? decision.selectedProvider().providerName() : null;
    }
}
