package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public final class RegistryAvailabilityProjection implements ProviderRegistryBoundary {
    private final com.example.platform.extension.api.port.CapabilityRegistryPort capabilities;
    private final com.example.platform.extension.api.port.PluginRegistryPort providers;
    private final Map<String, CapabilityAvailability> entries = new LinkedHashMap<>();
    public RegistryAvailabilityProjection(com.example.platform.extension.api.port.CapabilityRegistryPort capabilities,
                                         com.example.platform.extension.api.port.PluginRegistryPort providers) {
        this.capabilities=capabilities;
        this.providers=providers;
        register(new CapabilityAvailability("media.transcode", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("MediaAsset", "1"), Set.of("video"), Set.of("video/mp4", "video/webm"), Set.of(ExecutionMode.ASYNCHRONOUS, ExecutionMode.BATCH), Availability.AVAILABLE, "Transcode media assets", new CostEstimate(BigDecimal.ONE, "quota-unit", BigDecimal.ONE), new Reliability(true, true, 3), Set.of("media.template"), Set.of("media.application")));
        register(new CapabilityAvailability("media.thumbnail", "1.0", new ContractRef("MediaAsset", "1"), new ContractRef("ImageAsset", "1"), Set.of("image"), Set.of("video/mp4"), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.AVAILABLE, "Create a representative thumbnail", new CostEstimate(new BigDecimal("0.1"), "quota-unit", new BigDecimal("0.1")), new Reliability(true, true, 2), Set.of("media.template"), Set.of("media.application")));
    }
    private void register(CapabilityAvailability c) { entries.put(c.capabilityId()+":"+c.version(), c); }
    public List<CapabilityAvailability> publicAvailability() { return entries.values().stream().map(this::project).sorted(Comparator.comparing(CapabilityAvailability::capabilityId).thenComparing(CapabilityAvailability::version)).toList(); }
    public Optional<CapabilityAvailability> resolve(String id, String version) { return entries.values().stream().filter(c -> c.capabilityId().equals(id) && com.example.platform.composition.domain.CompositionVersionRange.check(version, c.version()).equals("OK")).sorted(Comparator.comparing(CapabilityAvailability::version)).findFirst().map(this::project); }
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
