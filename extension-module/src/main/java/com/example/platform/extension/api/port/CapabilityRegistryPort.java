package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityImplementation;
import com.example.platform.extension.domain.CapabilityImplementationId;
import com.example.platform.extension.domain.ContractVersion;

import java.util.List;
import java.util.Optional;

/**
 * #16 (R3/C10 + C16-CORR-3): CAPABILITY-FACING registry authority contract.
 *
 * <p>Capability consumers (Operation, Recipe, Skill, Agent, MCP, Application,
 * Capability Resolver) depend on THIS contract via {@link CapabilityRequirement}
 * — never on the plugin container contract. PluginRegistryPort is the plugin
 * package/container concern; PluginRegistryImpl is the current implementation
 * of both ports where repository structure justifies it.
 *
 * <p>The registry is discoverability + contract registration authority, NOT a
 * scheduler, entitlement engine, billing engine or marketplace.
 */
public interface CapabilityRegistryPort {

    /**
     * All registered capability implementations providing {@code capabilityId}
     * (deterministic order by implementation id). One plugin may provide
     * multiple distinct implementations of the same capability.
     *
     * @param capabilityId typed capability id
     * @return immutable implementation list
     */
    List<CapabilityImplementation> findCapabilityImplementations(CapabilityId capabilityId);

    /**
     * Lookup by independent implementation identity (NOT the (plugin, capability)
     * tuple — see {@link CapabilityImplementationId}).
     *
     * @param implementationId independent implementation id
     * @return implementation if registered
     */
    Optional<CapabilityImplementation> findImplementationById(CapabilityImplementationId implementationId);

    /**
     * Registered implementations providing {@code capabilityId} at exactly
     * {@code contractVersion}.
     *
     * @param capabilityId    typed capability id
     * @param contractVersion capability contract version
     * @return immutable implementation list
     */
    List<CapabilityImplementation> findImplementationsForContractVersion(
            CapabilityId capabilityId, ContractVersion contractVersion);
}
