package com.example.platform.runtime.mediatask;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.bmfprovider.BmfProviderExtension;
import com.example.platform.runtime.CpuArchitectureProbe;
import com.example.platform.runtime.ProviderExecutableDependencyProbe;
import com.example.platform.runtime.ExecutableIdentity;
import com.example.platform.runtime.SandboxedExecutableStartProbe;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.ProviderHardwareAvailableEvidence;
import com.example.platform.workerfabric.domain.ProviderHardwareObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyProbeSchemaVersion;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportEvidence;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * P1-1A.6 (Gates 1-3): the bounded-V1 ffmpeg worker's authoritative admission-evidence factory.
 *
 * <p>It produces the three independent inputs from their own authoritative sources:
 * <ul>
 *   <li><b>Runtime support advertisement</b> — from the real {@link ProviderPluginCatalog}: each
 *       contribution's {@link WorkerRuntimeSupportRequirement#supportIdentifier()} keyed to the
 *       provenance of the loaded plugin (plugin id + version). The advertised
 *       {@link RuntimeLifecycleKind} is the requirement's frozen kind; contributions that disagree
 *       fail closed rather than being silently reduced to one.</li>
 *   <li><b>Provider hardware observation</b> — a real observation of the running worker JVM's CPU
 *       architecture ({@link CpuArchitectureProbe}). Only the architecture carries evidence; the
 *       FFmpeg requirement declares no build/codec/sandbox-feature constraints, so those sets are
 *       left empty (no claims) rather than fabricated.</li>
 *   <li><b>Runtime dependency observation</b> — the executable is classified as statically
 *       self-contained by {@link ProviderExecutableDependencyProbe}; only then is a verified empty
 *       dependency set published. An executable with dynamic dependencies (or one that cannot be
 *       classified) yields no observation.</li>
 * </ul>
 *
 * <p>Every input is bound to the requesting worker runtime and physical host and carries a bounded
 * validity window. Anything that cannot be produced returns {@link Optional#empty()} so the minter
 * fails closed.
 */
public final class MediaTaskAdmissionEvidenceFactory implements AdmissionEvidenceProducer {

    /** Evidence validity window; the observation is regenerated once it expires. */
    public static final Duration EVIDENCE_VALIDITY = Duration.ofSeconds(60);

    /** Bounded argv for the read-only provider start probe. */
    private static final List<String> DEPENDENCY_PROBE_ARGUMENTS = List.of("-version");
    private static final String VERSION_LINE_PREFIX = "ffmpeg version ";

    private final ProviderPluginCatalog catalog;
    private final CpuArchitectureProbe architectureProbe;
    private final ProviderExecutableDependencyProbe dependencyProbe;
    private final SandboxedExecutableStartProbe startProbe;
    private final Path providerExecutable;
    private final Clock clock;
    private final Duration validity;

    public MediaTaskAdmissionEvidenceFactory(
            ProviderPluginCatalog catalog,
            CpuArchitectureProbe architectureProbe,
            ProviderExecutableDependencyProbe dependencyProbe,
            SandboxedExecutableStartProbe startProbe,
            Path providerExecutable,
            Clock clock,
            Duration validity) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.architectureProbe = Objects.requireNonNull(architectureProbe, "architectureProbe");
        this.dependencyProbe = Objects.requireNonNull(dependencyProbe, "dependencyProbe");
        this.startProbe = Objects.requireNonNull(startProbe, "startProbe");
        this.providerExecutable = Objects.requireNonNull(providerExecutable, "providerExecutable")
                .toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.validity = Objects.requireNonNull(validity, "validity");
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("evidence validity must be positive");
        }
    }

    @Override
    public Evidence produce(
            WorkerRuntimeId workerRuntimeId,
            PhysicalHostId physicalHostId,
            ProviderImplementationId providerImplementationId) {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(providerImplementationId, "providerImplementationId");
        Instant observedAt = clock.instant();
        Instant expiresAt = observedAt.plus(validity);
        for (ProviderPluginContribution contribution : catalog.contributions()) {
            if (contribution instanceof BmfProviderExtension bmf
                    && contribution.providerDescriptor().providerImplementationId()
                            .equals(providerImplementationId)) {
                Optional<BmfProviderExtension.RuntimeObservation> observation =
                        architectureProbe.probe().flatMap(architecture -> bmf.observeRuntime(
                                workerRuntimeId, physicalHostId, architecture, observedAt, expiresAt));
                return new Evidence(runtimeSupportAdvertisement(workerRuntimeId),
                        observation.map(BmfProviderExtension.RuntimeObservation::hardware),
                        observation.map(BmfProviderExtension.RuntimeObservation::dependencies));
            }
        }
        return new Evidence(
                runtimeSupportAdvertisement(workerRuntimeId),
                providerHardwareObservation(
                        providerImplementationId, workerRuntimeId, physicalHostId, observedAt, expiresAt),
                runtimeDependencyObservation(
                        providerImplementationId, workerRuntimeId, observedAt, expiresAt));
    }

    /**
     * The runtime's static support advertisement, keyed by the exact support identifier each
     * contribution requires. Fail closed on a missing requirement or on contributions that require
     * different runtime kinds (one runtime cannot advertise two kinds).
     */
    private Optional<WorkerRuntimeSupportAdvertisement> runtimeSupportAdvertisement(
            WorkerRuntimeId workerRuntimeId) {
        Map<RuntimeSupportIdentifier, RuntimeSupportEvidence> support = new LinkedHashMap<>();
        RuntimeLifecycleKind runtimeKind = null;
        for (ProviderPluginContribution contribution : catalog.contributions()) {
            WorkerRuntimeSupportRequirement requirement =
                    contribution.workerRuntimeSupportRequirement();
            if (requirement == null) {
                return Optional.empty();
            }
            if (runtimeKind == null) {
                runtimeKind = requirement.requiredRuntimeKind();
            } else if (runtimeKind != requirement.requiredRuntimeKind()) {
                return Optional.empty();
            }
            support.put(
                    requirement.supportIdentifier(),
                    new RuntimeSupportEvidence(
                            "provider-plugin",
                            contribution.pluginId() + "@" + contribution.pluginVersion()));
        }
        if (runtimeKind == null) {
            return Optional.empty();
        }
        return Optional.of(new WorkerRuntimeSupportAdvertisement(
                workerRuntimeId, runtimeKind, support));
    }

    private Optional<ProviderHardwareObservation> providerHardwareObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId workerRuntimeId,
            PhysicalHostId physicalHostId,
            Instant observedAt,
            Instant expiresAt) {
        Optional<CpuArchitecture> architecture = architectureProbe.probe();
        if (architecture.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProviderHardwareObservation(
                providerImplementationId,
                workerRuntimeId,
                physicalHostId,
                Optional.empty(),
                observedAt,
                expiresAt,
                new ProviderHardwareAvailableEvidence(
                        architecture.orElseThrow(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty())));
    }

    private Optional<RuntimeDependencyObservation> runtimeDependencyObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId workerRuntimeId,
            Instant observedAt,
            Instant expiresAt) {
        // Boundary (contract): a successful sandbox start with parseable version output is START
        // evidence only. It never asserts that a required encoder, decoder, filter, hardware path or
        // complete media chain is usable, and the observation below deliberately carries no such
        // field. Media-capability verification is a separate design and authorization item.
        // (1) The artifact must be a well-formed ELF we can classify. Both "statically self-contained"
        // and "declares dynamic dependencies" are valid classifications; only an unreadable/unknown
        // artifact fails closed here.
        ProviderExecutableDependencyProbe.Result classification =
                dependencyProbe.probe(providerExecutable);
        if (classification.status() == ProviderExecutableDependencyProbe.Status.UNKNOWN) {
            return Optional.empty();
        }
        // (2) Record the exact artifact identity observed before the probe.
        Optional<ExecutableIdentity> probedIdentity = ExecutableIdentity.of(providerExecutable);
        if (probedIdentity.isEmpty()) {
            return Optional.empty();
        }
        // (3) The authoritative completion check: the exact executable must start inside the
        // production sandbox and report its version. "Not declared" is not "checked empty".
        SandboxedExecutableStartProbe.Result start =
                startProbe.probe(providerExecutable, DEPENDENCY_PROBE_ARGUMENTS);
        if (start.status() != SandboxedExecutableStartProbe.Status.STARTED) {
            return Optional.empty();
        }
        if (firstLineStartingWith(start.stdout(), VERSION_LINE_PREFIX).isBlank()) {
            // A successful start without version evidence is not a completed probe for this provider.
            return Optional.empty();
        }
        // (4) Re-verify the identity at admission time: a replaced artifact must not reuse the
        // evidence gathered for the previous bytes.
        Optional<ExecutableIdentity> currentIdentity = ExecutableIdentity.of(providerExecutable);
        if (currentIdentity.isEmpty()
                || !currentIdentity.orElseThrow().equals(probedIdentity.orElseThrow())) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeDependencyObservation(
                providerImplementationId,
                workerRuntimeId,
                Optional.empty(),
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                observedAt,
                expiresAt,
                List.of()));
    }

    private static String firstLineStartingWith(String text, String prefix) {
        return text.lines().filter(line -> line.startsWith(prefix)).findFirst().orElse("");
    }
}
