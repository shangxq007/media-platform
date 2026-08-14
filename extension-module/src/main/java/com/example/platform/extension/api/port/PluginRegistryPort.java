package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginHealth;
import java.util.List;
import java.util.Optional;

/**
 * Public read/query port of the plugin registry (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>The registry is the ONE descriptor authority: it describes, validates,
 * registers metadata, queries and selects. It does NOT execute providers —
 * existing execution authority remains with
 * {@code ExtensionRegistryService} (Compatibility Model B).</p>
 *
 * <p>All reads return immutable snapshots; enumeration is deterministic
 * (stable plugin ID, then version). No registration-order-dependent API is
 * exposed. The mutation API is internal (startup registration only).</p>
 */
public interface PluginRegistryPort {

    /**
     * Looks up a registered descriptor by stable plugin ID.
     *
     * @param pluginId stable plugin ID
     * @return descriptor if registered
     */
    Optional<PluginDescriptor> findByPluginId(String pluginId);

    /**
     * Looks up a registered descriptor by plugin ID and version.
     *
     * @param pluginId      stable plugin ID
     * @param pluginVersion plugin version
     * @return descriptor if registered
     */
    Optional<PluginDescriptor> findByPluginIdAndVersion(String pluginId, String pluginVersion);

    /**
     * Deterministic enumeration (stable plugin ID, then version).
     *
     * @return immutable snapshot of all registered descriptors
     */
    List<PluginDescriptor> enumerate();

    /**
     * Capability candidate query: all registered plugins declaring the
     * capability ID and contract version.
     *
     * @param capabilityId                stable capability ID
     * @param capabilityContractVersion   capability contract version
     * @return deterministic candidate list
     */
    List<PluginDescriptor> findCapabilityCandidates(String capabilityId, String capabilityContractVersion);

    /**
     * Health association/query: current health of the plugin.
     *
     * @param pluginId stable plugin ID
     * @return health state (UNKNOWN when never evaluated)
     */
    PluginHealth healthOf(String pluginId);
}
