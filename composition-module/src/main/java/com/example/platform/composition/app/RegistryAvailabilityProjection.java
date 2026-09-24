package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public final class RegistryAvailabilityProjection implements CompositionProviderBoundCapabilityAuthority {
    private final com.example.platform.extension.api.port.CapabilityRegistryPort capabilities;
    private final com.example.platform.extension.api.port.PluginRegistryPort providers;
    private final Map<String, CapabilityAvailability> entries = new LinkedHashMap<>();
    public RegistryAvailabilityProjection(com.example.platform.extension.api.port.CapabilityRegistryPort capabilities,
                                         com.example.platform.extension.api.port.PluginRegistryPort providers) {
        this.capabilities=capabilities;
        this.providers=providers;
        // Capabilities expose only the platform Artifact contract. Raw provider
        // output remains behind the explicit materialization boundary.
        register(new CapabilityAvailability("media.transcode", "1.0", new ContractRef("Artifact", "1"), new ContractRef("Artifact", "1"), Set.of("video"), Set.of("video/mp4", "video/webm"), Set.of(ExecutionMode.ASYNCHRONOUS, ExecutionMode.BATCH), Availability.UNAVAILABLE, "MATERIALIZATION_REQUIRED: no typed output adapter is registered", new CostEstimate(BigDecimal.ONE, "quota-unit", BigDecimal.ONE), new Reliability(true, true, 3), Set.of("media.template"), Set.of("media.application")));
        register(new CapabilityAvailability("media.thumbnail", "1.0", new ContractRef("Artifact", "1"), new ContractRef("Artifact", "1"), Set.of("image"), Set.of("video/mp4"), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "Create a representative thumbnail", new CostEstimate(new BigDecimal("0.1"), "quota-unit", new BigDecimal("0.1")), new Reliability(true, true, 2), Set.of("media.template"), Set.of("media.application")));
    }
    private void register(CapabilityAvailability c) { entries.put(c.capabilityId()+":"+c.version(), c); }
    public List<CapabilityAvailability> publicAvailability() { return entries.values().stream().map(this::project).sorted(Comparator.comparing(CapabilityAvailability::capabilityId).thenComparing(CapabilityAvailability::version)).toList(); }
    public Optional<CapabilityAvailability> resolve(String id, String version) { return entries.values().stream().filter(c -> c.capabilityId().equals(id) && com.example.platform.composition.domain.CompositionVersionRange.check(version, c.version()).equals("OK")).sorted(Comparator.comparing(CapabilityAvailability::version)).findFirst().map(this::project); }
    @Override public Optional<ProviderBoundCapability> resolveProviderBound(String id, String version) {
        var capability = entries.values().stream().filter(c -> c.capabilityId().equals(id) && c.version().equals(version)).findFirst();
        if (capability.isEmpty()) return Optional.empty();
        var c = capability.get();
        var candidate = providers.findCapabilityCandidates(c.capabilityId(), c.version()).stream()
                .filter(p -> providers.healthOf(p.pluginId()).eligible()).findFirst();
        if (candidate.isEmpty()) return Optional.empty();
        var p = candidate.get();
        return Optional.of(new ProviderBoundCapability(c.capabilityId(), c.version(),
                p.pluginId() + "@" + p.pluginVersion(), c.version(), c.input().name(), c.input().version(),
                c.output().name(), c.output().version()));
    }
    private CapabilityAvailability project(CapabilityAvailability contract) {
        var matches=capabilities.findImplementationsForContractVersion(
                com.example.platform.extension.domain.CapabilityId.of(contract.capabilityId()),
                com.example.platform.extension.domain.ContractVersion.parse(contract.version()));
        boolean healthy=matches.stream().anyMatch(implementation -> providers.findByPluginId(implementation.pluginId())
                .filter(manifest -> manifest.capabilities().stream().anyMatch(c -> c.capabilityId().equals(contract.capabilityId())
                        && c.capabilityContractVersion().equals(contract.version())
                        && c.inputReferenceType().equals(contract.input().name())
                        && c.outputReferenceType().equals(contract.output().name())))
                .filter(manifest -> providers.healthOf(manifest.pluginId()).eligible())
                .isPresent());
        return new CapabilityAvailability(contract.capabilityId(),contract.version(),contract.input(),contract.output(),contract.assetTypes(),contract.mediaTypes(),contract.executionModes(),
                healthy?Availability.AVAILABLE:Availability.UNAVAILABLE,healthy?"Capability available":"No compatible healthy implementation",contract.estimate(),contract.reliability(),contract.compatibleWorkflowTypes(),contract.compatibleApplicationTypes());
    }
}
