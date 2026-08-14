package com.example.platform.extension.domain;

import java.util.Objects;

/**
 * #16 (R2/C5): one concrete realization of a capability.
 *
 * <p>A CapabilityImplementation is owned/provided by exactly one plugin and
 * realizes exactly one capability. It carries its own implementation version,
 * independent of the capability contract version and the plugin version.
 * Multiple distinct implementations of the same capability (same plugin or
 * different plugins) are supported; duplicate implementation ids fail closed
 * at the registry. Provider/worker never enter implementation identity.
 */
public record CapabilityImplementation(
        CapabilityImplementationId implementationId,
        String pluginId,
        CapabilityId capabilityId,
        ContractVersion contractVersion,
        String implementationVersion) {

    public CapabilityImplementation {
        Objects.requireNonNull(implementationId, "implementationId");
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(implementationVersion, "implementationVersion");
        if (pluginId.isBlank() || implementationVersion.isBlank()) {
            throw new IllegalArgumentException("pluginId and implementationVersion must not be blank");
        }
    }

    public static CapabilityImplementation of(
            CapabilityImplementationId implementationId,
            String pluginId,
            CapabilityId capabilityId,
            ContractVersion contractVersion,
            String implementationVersion) {
        return new CapabilityImplementation(implementationId, pluginId, capabilityId,
                contractVersion, implementationVersion);
    }

    @Override
    public String toString() {
        return implementationId + " (" + capabilityId + " v" + contractVersion
                + " impl " + implementationVersion + " by " + pluginId + ")";
    }
}
