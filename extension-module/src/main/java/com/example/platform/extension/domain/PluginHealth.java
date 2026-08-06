package com.example.platform.extension.domain;

/**
 * Plugin health state (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>The five-state health model is frozen: UNKNOWN, HEALTHY, DEGRADED,
 * UNHEALTHY, DISABLED. Health is derived (for FFmpeg, from existing tool
 * checks — {@code ToolRegistry.validateEnvironment()} and/or
 * {@code RenderToolCapabilityInventory.detectTools()}) and evaluated lazily;
 * P1 introduces no scheduled health polling, no background thread, no network
 * probe and no new process execution beyond existing checks.</p>
 *
 * <p>Selection eligibility (frozen deterministic rule):
 * eligible = {UNKNOWN, HEALTHY, DEGRADED}; ineligible = {UNHEALTHY, DISABLED}.</p>
 *
 * @param pluginId    stable plugin ID
 * @param state       health state
 */
public record PluginHealth(String pluginId, State state) {

    /** The frozen five health states. */
    public enum State {
        UNKNOWN,
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        DISABLED
    }

    public PluginHealth {
        if (pluginId == null) {
            throw new NullPointerException("pluginId must not be null");
        }
        if (state == null) {
            throw new NullPointerException("state must not be null");
        }
    }

    public static PluginHealth unknown(String pluginId) {
        return new PluginHealth(pluginId, State.UNKNOWN);
    }

    /**
     * Deterministic eligibility rule:
     * eligible = {UNKNOWN, HEALTHY, DEGRADED}; ineligible = {UNHEALTHY, DISABLED}.
     */
    public boolean eligible() {
        return state == State.UNKNOWN || state == State.HEALTHY || state == State.DEGRADED;
    }
}
