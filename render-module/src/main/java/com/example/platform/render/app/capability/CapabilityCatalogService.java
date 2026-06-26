package com.example.platform.render.app.capability;

import com.example.platform.render.domain.capability.CapabilityDescriptor;
import com.example.platform.render.domain.producer.Producer;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Capability Catalog — authoritative metadata source for planning.
 * Auto-discovers Producer implementations via Spring injection.
 * No registry table. No persistence.
 */
@Service
public class CapabilityCatalogService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityCatalogService.class);
    private final Map<String, List<CapabilityDescriptor>> capabilityIndex = new LinkedHashMap<>();
    private final Map<String, CapabilityDescriptor> producerIndex = new LinkedHashMap<>();

    public CapabilityCatalogService(List<Producer> allProducers) {
        for (Producer p : allProducers) {
            CapabilityDescriptor desc = p.descriptor();
            capabilityIndex.computeIfAbsent(desc.capability(), k -> new ArrayList<>()).add(desc);
            producerIndex.put(desc.producerId(), desc);
            log.info("Capability catalog: registered {} → {} (backend={})",
                    desc.capability(), desc.producerId(), desc.backendId());
        }
    }

    public List<CapabilityDescriptor> listCapabilities() {
        return capabilityIndex.values().stream().flatMap(Collection::stream).toList();
    }

    public List<CapabilityDescriptor> candidatesFor(String capability) {
        return capabilityIndex.getOrDefault(capability, List.of());
    }

    public Optional<CapabilityDescriptor> resolvePreferred(String capability) {
        return candidatesFor(capability).stream()
                .filter(CapabilityDescriptor::preferred)
                .findFirst();
    }

    public Optional<CapabilityDescriptor> resolve(String capability) {
        var candidates = candidatesFor(capability);
        if (candidates.isEmpty()) return Optional.empty();
        return candidates.stream()
                .filter(CapabilityDescriptor::enabled)
                .max(Comparator.comparingInt(CapabilityDescriptor::priority));
    }

    public Map<String, List<CapabilityDescriptor>> catalog() {
        return Collections.unmodifiableMap(capabilityIndex);
    }

    public int size() { return producerIndex.size(); }
}
