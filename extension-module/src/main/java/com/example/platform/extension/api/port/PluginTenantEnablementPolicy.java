package com.example.platform.extension.api.port;

/**
 * Tenant enablement policy port (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 boundary: tenant enablement is a STABLE POLICY PORT — interface only.
 * Matching consults the policy when present; the default behavior is
 * TRUSTED_INTERNAL_ENABLED (trusted internal plugins enabled for all
 * tenants). No persistent tenant plugin configuration, no tenant plugin
 * table, no tenant install state and no per-tenant matching state exist in
 * P1.</p>
 */
public interface PluginTenantEnablementPolicy {

    /**
     * Whether the plugin is enabled for the tenant.
     *
     * @param pluginId stable plugin ID
     * @param tenantId tenant context
     * @return enabled flag
     */
    boolean isEnabled(String pluginId, String tenantId);

    /**
     * Default trusted-internal policy: enabled for all tenants.
     */
    static PluginTenantEnablementPolicy trustedInternal() {
        return (pluginId, tenantId) -> true;
    }
}
