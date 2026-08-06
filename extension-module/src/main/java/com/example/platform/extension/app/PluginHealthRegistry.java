package com.example.platform.extension.app;

import com.example.platform.extension.domain.PluginHealth;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/**
 * Plugin health authority (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>The SINGLE plugin-health authority. Five states: UNKNOWN, HEALTHY,
 * DEGRADED, UNHEALTHY, DISABLED. Health is derived from existing tool checks
 * (ToolRegistry.validateEnvironment / RenderToolCapabilityInventory.detectTools)
 * at registration/read time — no scheduled polling, no background thread, no
 * network probe, no new process execution in P1.</p>
 *
 * <p>Selection eligibility (frozen): eligible = {UNKNOWN, HEALTHY, DEGRADED};
 * ineligible = {UNHEALTHY, DISABLED}.</p>
 *
 * <p>Update is an internal record/update API; public reads are read-only
 * queries. No public mutation of arbitrary health state is exposed.</p>
 */
@Service
public class PluginHealthRegistry {

    private final ConcurrentMap<String, PluginHealth> healthByPluginId = new ConcurrentHashMap<>();

    /**
     * Internal record/update: associates the plugin with a health state.
     * Used by the FFmpeg self-description contributor at startup registration
     * (derived from existing tool checks).
     */
    public void record(String pluginId, PluginHealth.State state) {
        healthByPluginId.put(pluginId, new PluginHealth(pluginId, state));
    }

    /** Ensures a plugin entry exists (UNKNOWN when never evaluated). */
    public void touch(String pluginId) {
        healthByPluginId.putIfAbsent(pluginId, PluginHealth.unknown(pluginId));
    }

    /**
     * Public read-only query.
     *
     * @param pluginId stable plugin ID
     * @return current health (UNKNOWN when never evaluated)
     */
    public PluginHealth healthOf(String pluginId) {
        return healthByPluginId.getOrDefault(pluginId, PluginHealth.unknown(pluginId));
    }

    void resetForTests() {
        healthByPluginId.clear();
    }
}
