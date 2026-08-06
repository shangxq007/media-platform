package com.example.platform.extension.app;

import com.example.platform.extension.api.port.PluginRegistryPort;
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import com.example.platform.extension.domain.PluginDiagnosticCode;
import com.example.platform.extension.domain.PluginHealth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Plugin registry implementation (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>The registry is the ONE descriptor authority (describes, validates,
 * registers metadata, queries and selects). It does NOT execute providers —
 * existing execution authority remains with {@code ExtensionRegistryService}
 * (Compatibility Model B). Registration lifecycle: STARTUP_REGISTRATION only.
 * No install/update/remove/persistence/hot-reload/marketplace.</p>
 *
 * <p>Thread safety: ConcurrentHashMap-backed; atomic register; reads safe
 * concurrently with registration. Storage is order-independent; enumeration is
 * always sorted by stable ID (then version). Registration is order-independent:
 * no first-wins. Immutable read snapshots: enumerate/find return immutable
 * copies; descriptors are immutable records.</p>
 */
@Service
public class PluginRegistryImpl implements PluginRegistryPort {

    private final PluginDescriptorValidator validator;
    private final PluginHealthRegistry healthRegistry;
    private final ConcurrentMap<String, PluginDescriptor> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PluginDescriptor> byIdAndVersion = new ConcurrentHashMap<>();

    @Autowired
    public PluginRegistryImpl(PluginDescriptorValidator validator, PluginHealthRegistry healthRegistry) {
        this.validator = validator;
        this.healthRegistry = healthRegistry;
    }

    /** Test-only convenience constructor. */
    PluginRegistryImpl() {
        this(new PluginDescriptorValidator(), new PluginHealthRegistry());
    }

    /**
     * Validated startup registration. Invalid descriptors are NOT registered:
     * zero registry mutation, zero partial state. Duplicate plugin identity
     * (pluginId+version) is rejected with PLG-015.
     *
     * @param descriptor descriptor to register
     * @return ordered validation diagnostics; empty when registration succeeded
     */
    public List<PluginDescriptorValidationIssue> register(PluginDescriptor descriptor) {
        List<PluginDescriptorValidationIssue> issues = validator.validate(descriptor);
        if (!issues.isEmpty()) {
            return issues;
        }
        String key = descriptor.pluginId();
        String keyVersioned = descriptor.pluginId() + "@" + descriptor.pluginVersion();
        PluginDescriptor previous = byId.putIfAbsent(key, descriptor);
        if (previous != null) {
            return List.of(PluginDescriptorValidationIssue.error(
                    PluginDiagnosticCode.PLG_015, "pluginId", 1));
        }
        byIdAndVersion.put(keyVersioned, descriptor);
        healthRegistry.touch(descriptor.pluginId());
        return List.of();
    }

    @Override
    public Optional<PluginDescriptor> findByPluginId(String pluginId) {
        return Optional.ofNullable(byId.get(pluginId));
    }

    @Override
    public Optional<PluginDescriptor> findByPluginIdAndVersion(String pluginId, String pluginVersion) {
        return Optional.ofNullable(byIdAndVersion.get(pluginId + "@" + pluginVersion));
    }

    @Override
    public List<PluginDescriptor> enumerate() {
        List<PluginDescriptor> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparing(PluginDescriptor::pluginId)
                .thenComparing(PluginDescriptor::pluginVersion));
        return List.copyOf(sorted);
    }

    @Override
    public List<PluginDescriptor> findCapabilityCandidates(String capabilityId, String capabilityContractVersion) {
        List<PluginDescriptor> candidates = new ArrayList<>();
        for (PluginDescriptor descriptor : byId.values()) {
            for (CapabilityDescriptor capability : descriptor.capabilities()) {
                if (capability.capabilityId().equals(capabilityId)
                        && capability.capabilityContractVersion().equals(capabilityContractVersion)) {
                    candidates.add(descriptor);
                    break;
                }
            }
        }
        candidates.sort(Comparator.comparing(PluginDescriptor::pluginId)
                .thenComparing(PluginDescriptor::pluginVersion));
        return List.copyOf(candidates);
    }

    @Override
    public PluginHealth healthOf(String pluginId) {
        return healthRegistry.healthOf(pluginId);
    }

    /** Package-private test reset (frozen contract: "TEST RESET: package-private reset for tests only"). */
    void resetForTests() {
        byId.clear();
        byIdAndVersion.clear();
        healthRegistry.resetForTests();
    }
}
