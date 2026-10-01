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
    /**
     * Capabilities that are registered for discovery but whose platform execution path is
     * deliberately not wired yet.
     *
     * <p>Their availability is pinned UNAVAILABLE here, independently of the reference-type names a
     * provider manifest happens to declare, so that aligning those strings cannot silently advertise
     * a capability the platform cannot dispatch. Removing an entry is the reviewable step that makes
     * the capability composable, and the pinned reason is the summary callers observe. An entry in
     * this map is never {@code AVAILABLE} and never resolves to a provider binding.
     *
     * <p>COVER-THUMBNAIL-REBUILD-001 (action 1): {@code media.cover-image} no longer has a pending
     * entry. Its contribution now declares the platform Artifact capability contract
     * ({@code media.cover-image} subject Artifact in / cover Artifact out) that the catalog publishes,
     * so its availability is derived from the registered, healthy implementation exactly like
     * {@code media.thumbnail} — not pinned. The map is kept as the explicit fail-closed seam for any
     * future capability whose platform execution path is genuinely not wired.
     */
    private static final Map<String, String> PENDING_PLATFORM_DISPATCH = Map.of();
    public RegistryAvailabilityProjection(com.example.platform.extension.api.port.CapabilityRegistryPort capabilities,
                                         com.example.platform.extension.api.port.PluginRegistryPort providers) {
        this.capabilities=capabilities;
        this.providers=providers;
        // COVER-PROVIDER-PLATFORM-REGISTER-001: media.cover-image is a first-class capability of
        // the platform provider family platform.ffmpeg (implementation ffmpeg.cpu.frame-extract.v1,
        // declared by CoverImagePlatformProvider and registered in the capability registry by
        // CoverImagePlatformRegistration). The capability contract is the platform Artifact contract
        // (subject Artifact in, cover Artifact out, committed through ArtifactCommitService).
        // COVER-THUMBNAIL-REBUILD-001 (action 1): the contribution now declares that same Artifact
        // capability contract, so this entry's availability is derived from the registered, healthy
        // implementation (media.thumbnail is registered the same way) instead of being pinned.
        register(new CapabilityAvailability("media.cover-image", "1.0", new ContractRef("Artifact", "1"), new ContractRef("Artifact", "1"), Set.of("image"), Set.of("video/mp4", "video/webm", "video/quicktime", "video/x-matroska"), Set.of(ExecutionMode.ASYNCHRONOUS), Availability.UNAVAILABLE, "SLICE_LOCAL_RUNTIME: cover render runs in the cover worker; platform execution-seam integration (operation invocation boundary) is pending", new CostEstimate(new BigDecimal("0.1"), "quota-unit", new BigDecimal("0.1")), new Reliability(true, true, 3), Set.of("media.template"), Set.of("media.application")));
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
        // Fail closed: a capability the catalog does not report AVAILABLE must not resolve to a
        // provider binding. The gate is the single availability computation used by the projection,
        // so resolution and the published catalog can never disagree.
        if (!dispatchAvailable(c)) return Optional.empty();
        var candidate = providers.findCapabilityCandidates(c.capabilityId(), c.version()).stream()
                .filter(p -> providers.healthOf(p.pluginId()).eligible()).findFirst();
        if (candidate.isEmpty()) return Optional.empty();
        var p = candidate.get();
        return Optional.of(new ProviderBoundCapability(c.capabilityId(), c.version(),
                p.pluginId() + "@" + p.pluginVersion(), c.version(), c.input().name(), c.input().version(),
                c.output().name(), c.output().version()));
    }
    /**
     * Single availability computation shared by the published catalog and provider-bound resolution:
     * pinned UNAVAILABLE for capabilities with no platform execution seam yet, otherwise derived from
     * a registered implementation whose manifest declares the same capability, contract version and
     * reference types and whose provider is health-eligible.
     */
    private boolean dispatchAvailable(CapabilityAvailability contract) {
        if (PENDING_PLATFORM_DISPATCH.containsKey(contract.capabilityId()+":"+contract.version())) return false;
        var matches=capabilities.findImplementationsForContractVersion(
                com.example.platform.shared.capability.CapabilityId.of(contract.capabilityId()),
                com.example.platform.shared.capability.ContractVersion.parse(contract.version()));
        return matches.stream().anyMatch(implementation -> providers.findByPluginId(implementation.pluginId())
                .filter(manifest -> manifest.capabilities().stream().anyMatch(c -> c.capabilityId().equals(contract.capabilityId())
                        && c.capabilityContractVersion().equals(contract.version())
                        && c.inputReferenceType().equals(contract.input().name())
                        && c.outputReferenceType().equals(contract.output().name())))
                .filter(manifest -> providers.healthOf(manifest.pluginId()).eligible())
                .isPresent());
    }
    private CapabilityAvailability project(CapabilityAvailability contract) {
        boolean healthy = dispatchAvailable(contract);
        String pending = PENDING_PLATFORM_DISPATCH.get(contract.capabilityId()+":"+contract.version());
        return new CapabilityAvailability(contract.capabilityId(),contract.version(),contract.input(),contract.output(),contract.assetTypes(),contract.mediaTypes(),contract.executionModes(),
                healthy?Availability.AVAILABLE:Availability.UNAVAILABLE,
                healthy?"Capability available":(pending!=null?pending:"No compatible healthy implementation"),
                contract.estimate(),contract.reliability(),contract.compatibleWorkflowTypes(),contract.compatibleApplicationTypes());
    }
}
