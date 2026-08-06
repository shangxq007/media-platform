package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.PluginDescriptor;

/**
 * Deterministic selection policy port (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Provides deterministic priority values used by the matcher's selection
 * pipeline. The default policy assigns equal priority; explicit request and
 * stable identity ordering remain the deterministic fallbacks. This policy is
 * the deterministic candidate-ordering authority — it is ORTHOGONAL to
 * {@code ExtensionRouter} (which performs canary version-traffic routing and
 * is not part of the selection pipeline).</p>
 */
public interface PluginSelectionPolicy {

    /**
     * Deterministic priority of a descriptor. Higher value = higher priority.
     *
     * @param descriptor candidate descriptor
     * @return priority value (must be deterministic for the same descriptor)
     */
    int priority(PluginDescriptor descriptor);
}
