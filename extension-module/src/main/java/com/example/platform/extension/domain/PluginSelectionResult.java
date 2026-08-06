package com.example.platform.extension.domain;

/**
 * Deterministic matching/selection result (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Immutable value type produced by the matcher. Carries stable identity and
 * version information only — never implementation classes, repositories or
 * runtime internals.</p>
 *
 * @param pluginId               stable plugin ID
 * @param pluginVersion          plugin version
 * @param capabilityId           matched capability ID
 * @param capabilityContractVersion matched capability contract version
 * @param handledObjectTypeId    matched handled-object type ID
 * @param healthState            health state at selection time (eligibility applied)
 */
public record PluginSelectionResult(
        String pluginId,
        String pluginVersion,
        String capabilityId,
        String capabilityContractVersion,
        String handledObjectTypeId,
        PluginHealth.State healthState) {
}
