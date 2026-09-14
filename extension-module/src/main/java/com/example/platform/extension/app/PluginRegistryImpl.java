package com.example.platform.extension.app;

import com.example.platform.extension.api.port.*;
import com.example.platform.extension.domain.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * One descriptor authority, with capability discovery derived from each immutable entry.
 * All derivation precedes publication. The host owns PF4J lifecycle; Extension owns
 * registration/retirement. Runtime handles retire only their own entry, not a later
 * registration with the same ID/version. No execution, installation or hot reload here.
 */
@Service
public class PluginRegistryImpl implements PluginRegistryPort, CapabilityRegistryPort, PluginRegistrationPort {
    private final PluginDescriptorValidator validator;
    private final PluginHealthRegistry healthRegistry;
    private final Map<String, Entry> byId = new HashMap<>();

    private record Entry(PluginDescriptor descriptor, List<CapabilityImplementation> implementations) {}

    @Autowired
    public PluginRegistryImpl(PluginDescriptorValidator validator, PluginHealthRegistry healthRegistry) {
        this.validator = Objects.requireNonNull(validator);
        this.healthRegistry = Objects.requireNonNull(healthRegistry);
    }
    PluginRegistryImpl() { this(new PluginDescriptorValidator(), new PluginHealthRegistry()); }

    @Override
    public List<PluginDescriptorValidationIssue> validate(PluginDescriptor descriptor) {
        var issues = validator.validate(descriptor);
        if (issues.isEmpty()) deriveImplementations(descriptor);
        return issues;
    }

    /** Retains the existing diagnostic-returning startup registration contract. */
    public synchronized List<PluginDescriptorValidationIssue> register(PluginDescriptor descriptor) {
        try { insert(descriptor); return List.of(); }
        catch (PluginRegistrationException rejected) { return rejected.issues(); }
    }

    @Override
    public synchronized Registration registerRuntime(PluginDescriptor descriptor) {
        Entry entry = insert(descriptor);
        return () -> retire(entry);
    }

    private Entry insert(PluginDescriptor descriptor) {
        var issues = validator.validate(descriptor);
        if (!issues.isEmpty()) throw new PluginRegistrationException(issues);
        if (byId.containsKey(descriptor.pluginId())) throw new PluginRegistrationException(List.of(
                PluginDescriptorValidationIssue.error(PluginDiagnosticCode.PLG_015, "pluginId", 1)));
        // Domain version/identity validation can throw. Do it before ANY advertised entry.
        Entry entry = new Entry(descriptor, deriveImplementations(descriptor));
        healthRegistry.touch(descriptor.pluginId());
        byId.put(descriptor.pluginId(), entry);
        return entry;
    }

    private static List<CapabilityImplementation> deriveImplementations(PluginDescriptor descriptor) {
        List<CapabilityImplementation> result = new ArrayList<>();
        Set<CapabilityImplementationId> identities = new HashSet<>();
        for (CapabilityDescriptor capability : descriptor.capabilities()) {
            CapabilityImplementationId id = CapabilityImplementationId.of(descriptor.pluginId() + "::"
                    + capability.capabilityId() + "@" + capability.capabilityContractVersion());
            if (!identities.add(id)) throw new PluginRegistrationException(List.of(
                    PluginDescriptorValidationIssue.error(PluginDiagnosticCode.PLG_018, "capabilityImplementationId", 1)));
            result.add(CapabilityImplementation.of(id, descriptor.pluginId(), CapabilityId.of(capability.capabilityId()),
                    ContractVersion.parse(capability.capabilityContractVersion()), descriptor.pluginVersion()));
        }
        return List.copyOf(result);
    }

    private synchronized void retire(Entry entry) {
        String id = entry.descriptor().pluginId();
        if (byId.get(id) == entry) {
            byId.remove(id);
            healthRegistry.remove(id);
        }
    }

    private List<CapabilityImplementation> implementations() {
        return byId.values().stream().flatMap(e -> e.implementations().stream())
                .sorted(Comparator.comparing(i -> i.implementationId().value())).toList();
    }
    @Override public synchronized List<CapabilityImplementation> findCapabilityImplementations(CapabilityId id) {
        return implementations().stream().filter(i -> i.capabilityId().equals(id)).toList();
    }
    @Override public synchronized Optional<CapabilityImplementation> findImplementationById(CapabilityImplementationId id) {
        return implementations().stream().filter(i -> i.implementationId().equals(id)).findFirst();
    }
    @Override public synchronized List<CapabilityImplementation> findImplementationsForContractVersion(CapabilityId id, ContractVersion version) {
        return implementations().stream().filter(i -> i.capabilityId().equals(id) && i.contractVersion().equals(version)).toList();
    }
    @Override public synchronized Optional<PluginDescriptor> findByPluginId(String id) {
        return Optional.ofNullable(byId.get(id)).map(Entry::descriptor);
    }
    @Override public synchronized Optional<PluginDescriptor> findByPluginIdAndVersion(String id, String version) {
        return findByPluginId(id).filter(d -> d.pluginVersion().equals(version));
    }
    @Override public synchronized List<PluginDescriptor> enumerate() {
        return byId.values().stream().map(Entry::descriptor)
                .sorted(Comparator.comparing(PluginDescriptor::pluginId).thenComparing(PluginDescriptor::pluginVersion)).toList();
    }
    @Override public synchronized List<PluginDescriptor> findCapabilityCandidates(String id, String version) {
        return enumerate().stream().filter(d -> d.capabilities().stream()
                .anyMatch(c -> c.capabilityId().equals(id) && c.capabilityContractVersion().equals(version))).toList();
    }
    @Override public synchronized PluginHealth healthOf(String id) { return healthRegistry.healthOf(id); }
    synchronized void resetForTests() { byId.clear(); healthRegistry.resetForTests(); }
}
