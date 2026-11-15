package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.providerplugin.InProcessProviderPlugins;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.bmf.BmfProviderExtension;
import com.example.platform.runtime.mediatask.MediaTaskAdmissionEvidenceFactory;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.ProviderHardwareConformanceEvaluator;
import com.example.platform.workerfabric.domain.ProviderHardwareAvailableEvidence;
import com.example.platform.workerfabric.domain.RuntimeDependencyMatcher;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

/**
 * P1-1A.7: the runtime-dependency input is completed only when the exact executable starts in the
 * production sandbox, reports version evidence, and its identity is unchanged at emission time.
 */
class MediaTaskAdmissionEvidenceFactoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("local-ffmpeg");
    private static final PhysicalHostId HOST = PhysicalHostId.of("local");
    private static final ProviderImplementationId IMPLEMENTATION =
            ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1");
    private static final String HEALTHY_STDOUT =
            "ffmpeg version 7.0.2-static Copyright (c) 2000-2024\nconfiguration: --enable-libx264\n";

    @TempDir Path temp;

    @Test
    void verifiedBmfBundleProducesItsOwnAdmissionEvidence() throws IOException {
        String root = System.getenv("BMF_TEST_RUNTIME_ROOT");
        Assumptions.assumeTrue(root != null, "controlled BMF runtime is not configured");
        var bmf = new BmfProviderExtension(Path.of(root));
        var factory = factory(catalog(bmf),
                writeElf("unused-ffmpeg", 1), new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.FAILED, ""));

        var evidence = factory.produce(RUNTIME, HOST,
                ProviderImplementationId.of("bmf.cpu.v1"));
        assertThat(evidence.runtimeSupportAdvertisement()).isPresent();
        var hardware = (ProviderHardwareAvailableEvidence)
                evidence.providerHardwareObservation().orElseThrow().evidence();
        assertThat(hardware.providerBuildFeatures()).contains("bmf.cpu.enabled");
        assertThat(evidence.runtimeDependencyObservation().orElseThrow().dependencies())
                .extracting(dependency -> dependency.coordinate().value())
                .containsExactly("bmf", "ffmpeg", "numpy", "python");
        assertThat(ProviderHardwareConformanceEvaluator.evaluate(
                bmf.providerDescriptor().providerImplementationId(), RUNTIME, HOST,
                java.util.Optional.empty(), bmf.providerHardwareRequirement().orElseThrow(),
                evidence.providerHardwareObservation(), CLOCK.instant()).canRun()).isTrue();
        assertThat(RuntimeDependencyMatcher.match(
                bmf.providerDescriptor().providerImplementationId(), RUNTIME,
                java.util.Optional.empty(), bmf.runtimeDependencyRequirements(),
                evidence.runtimeDependencyObservation(), CLOCK.instant()).canMatch()).isTrue();
    }

    @Test
    void unverifiedBmfBundleFailsClosedAtAdmission() throws IOException {
        var factory = factory(catalog(new BmfProviderExtension(temp)),
                writeElf("unused-ffmpeg", 1), new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));
        var evidence = factory.produce(RUNTIME, HOST,
                ProviderImplementationId.of("bmf.cpu.v1"));
        assertThat(evidence.providerHardwareObservation()).isEmpty();
        assertThat(evidence.runtimeDependencyObservation()).isEmpty();
    }

    @Test
    void producesTheThreeIndependentInputsFromTheirOwnAuthorities() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("static-ffmpeg", 1), // PT_LOAD only -> statically self-contained
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        var evidence = factory.produce(RUNTIME, HOST, IMPLEMENTATION);

        var advertisement = evidence.runtimeSupportAdvertisement().orElseThrow();
        assertThat(advertisement.runtimeId()).isEqualTo(RUNTIME);
        assertThat(advertisement.runtimeKind()).isEqualTo(RuntimeLifecycleKind.EPHEMERAL_TASK);
        assertThat(advertisement.staticSupportEvidence())
                .containsKey(RuntimeSupportIdentifier.of("ffmpeg.cpu.v1"));

        var hardware = evidence.providerHardwareObservation().orElseThrow();
        assertThat(hardware.evidence()).isInstanceOf(ProviderHardwareAvailableEvidence.class);
        assertThat(((ProviderHardwareAvailableEvidence) hardware.evidence()).cpuArchitecture())
                .isEqualTo(CpuArchitecture.X86_64);

        var dependency = evidence.runtimeDependencyObservation().orElseThrow();
        assertThat(dependency.providerImplementationId()).isEqualTo(IMPLEMENTATION);
        assertThat(dependency.workerRuntimeId()).isEqualTo(RUNTIME);
        assertThat(dependency.dependencies()).isEmpty();
    }

    @Test
    void aDynamicallyLinkedExecutableThatStartsInTheSandboxIsAccepted() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("dynamic-ffmpeg", 1, 2), // PT_LOAD + PT_DYNAMIC
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        var evidence = factory.produce(RUNTIME, HOST, IMPLEMENTATION);

        assertThat(evidence.runtimeDependencyObservation()).isPresent();
        assertThat(evidence.runtimeDependencyObservation().orElseThrow().dependencies()).isEmpty();
    }

    @Test
    void aStartProbeThatDoesNotStartYieldsNoDependencyObservation() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("dynamic-ffmpeg", 1, 2),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.FAILED, ""));

        var evidence = factory.produce(RUNTIME, HOST, IMPLEMENTATION);

        assertThat(evidence.runtimeDependencyObservation()).isEmpty();
    }

    @Test
    void aTimedOutStartIsNeverTranslatedIntoAValidDependencyObservation() throws IOException {
        // A wall-clock termination (launcher PROCESS_TIMEOUT) is a failed start: no observation.
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("dynamic-ffmpeg", 1, 2),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.FAILED, "PROCESS_TIMEOUT"));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeDependencyObservation())
                .isEmpty();
    }

    @Test
    void aSuccessfulStartIsStartEvidenceOnlyAndNeverAMediaCapabilityProof() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("dynamic-ffmpeg", 1, 2),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        var evidence = factory.produce(RUNTIME, HOST, IMPLEMENTATION);

        // Start + version evidence was satisfied, so the dependency observation exists ...
        assertThat(evidence.runtimeDependencyObservation()).isPresent();
        assertThat(evidence.runtimeDependencyObservation().orElseThrow().dependencies()).isEmpty();
        // ... but the start probe claims no codec/filter/device capability of its own.
        var hardware = (ProviderHardwareAvailableEvidence)
                evidence.providerHardwareObservation().orElseThrow().evidence();
        assertThat(hardware.providerBuildFeatures()).isEmpty();
        assertThat(hardware.codecOrFilterFeatures()).isEmpty();
        assertThat(hardware.sandboxPermissions()).isEmpty();
        assertThat(hardware.deviceEvidence()).isEmpty();
        // ... and the runtime-dependency evidence model cannot even express a media-capability claim,
        // so admission cannot read a successful start as "encoder/decoder/filter verified".
        assertThat(Arrays.stream(RuntimeDependencyObservation.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .as("runtime dependency evidence must carry no media-capability component")
                .noneMatch(name -> name.matches(
                        "(?i).*(codec|filter|encoder|decoder|media|capability|hardware).*"));
    }

    @Test
    void aStartWithoutVersionEvidenceYieldsNoDependencyObservation() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("dynamic-ffmpeg", 1, 2),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, "no version here\n"));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeDependencyObservation())
                .isEmpty();
    }

    @Test
    void aNonElfArtifactYieldsNoDependencyObservation() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeText("not-elf"),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeDependencyObservation())
                .isEmpty();
    }

    @Test
    void anExecutableReplacedAfterTheProbeYieldsNoDependencyObservation() throws IOException {
        Path executable = writeElf("replaced-ffmpeg", 1, 2);
        // The probe stands in for the TOCTOU window: the artifact changes between probing and emission.
        SandboxedExecutableStartProbe replacingProbe = (path, arguments) -> {
            try {
                Files.write(path, elf64(1, 2, 2));
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            return new SandboxedExecutableStartProbe.Result(
                    SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT, "replaced");
        };
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                executable,
                new CpuArchitectureProbe(),
                replacingProbe);

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeDependencyObservation())
                .isEmpty();
    }

    @Test
    void contributionsRequiringDifferentRuntimeKindsCannotShareOneAdvertisement()
            throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(
                        contribution("a.provider", "1.0.0", RuntimeLifecycleKind.EPHEMERAL_TASK),
                        contribution("b.provider", "1.0.0", RuntimeLifecycleKind.RESIDENT_RUNTIME)),
                writeElf("static-ffmpeg", 1),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeSupportAdvertisement())
                .isEmpty();
    }

    @Test
    void anEmptyCatalogCannotAdvertiseRuntimeSupport() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                new ProviderPluginCatalog(),
                writeElf("static-ffmpeg", 1),
                new CpuArchitectureProbe(),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).runtimeSupportAdvertisement())
                .isEmpty();
    }

    @Test
    void anUnknownArchitectureYieldsNoHardwareObservation() throws IOException {
        MediaTaskAdmissionEvidenceFactory factory = factory(
                catalog(contribution("media.transcode.ffmpeg", "1.0.0",
                        RuntimeLifecycleKind.EPHEMERAL_TASK)),
                writeElf("static-ffmpeg", 1),
                new CpuArchitectureProbe(() -> "sparc"),
                new FakeStartProbe(SandboxedExecutableStartProbe.Status.STARTED, HEALTHY_STDOUT));

        assertThat(factory.produce(RUNTIME, HOST, IMPLEMENTATION).providerHardwareObservation())
                .isEmpty();
    }

    // ---------- fixtures ----------

    private MediaTaskAdmissionEvidenceFactory factory(
            ProviderPluginCatalog catalog,
            Path executable,
            CpuArchitectureProbe architectureProbe,
            SandboxedExecutableStartProbe startProbe) {
        return new MediaTaskAdmissionEvidenceFactory(
                catalog,
                architectureProbe,
                new ProviderExecutableDependencyProbe(),
                startProbe,
                executable,
                CLOCK,
                Duration.ofSeconds(60));
    }

    /** Deterministic start probe for the producer logic; no sandbox is touched. */
    private static final class FakeStartProbe implements SandboxedExecutableStartProbe {

        private final Status status;
        private final String stdout;

        private FakeStartProbe(Status status, String stdout) {
            this.status = status;
            this.stdout = stdout;
        }

        @Override
        public Result probe(Path executable, List<String> arguments) {
            return new Result(status, stdout, "test double");
        }
    }

    private static ProviderPluginCatalog catalog(ProviderPluginContribution... contributions) {
        return InProcessProviderPlugins.catalogWith(
                new ProviderPluginCatalog(), List.of(contributions));
    }

    private static ProviderPluginContribution contribution(
            String pluginId, String pluginVersion, RuntimeLifecycleKind runtimeKind) {
        ProviderPluginContribution contribution = mock(ProviderPluginContribution.class);
        when(contribution.pluginId()).thenReturn(pluginId);
        when(contribution.pluginVersion()).thenReturn(pluginVersion);
        ProviderBindingPin bindingPin = bindingPin(pluginId);
        when(contribution.providerBindingPin()).thenReturn(bindingPin);
        when(contribution.workerRuntimeSupportRequirement()).thenReturn(
                new WorkerRuntimeSupportRequirement(
                        bindingPin,
                        runtimeKind,
                        RuntimeSupportIdentifier.of("ffmpeg.cpu.v1")));
        return contribution;
    }

    private static ProviderBindingPin bindingPin(String name) {
        return new ProviderBindingPin(
                ProviderId.of(name),
                ProviderImplementationId.of(name + ".native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private Path writeElf(String name, int... programHeaderTypes) throws IOException {
        Path path = temp.resolve(name);
        Files.write(path, elf64(programHeaderTypes));
        path.toFile().setExecutable(true);
        return path;
    }

    private Path writeText(String name) throws IOException {
        Path path = temp.resolve(name);
        Files.write(path, "not an elf".getBytes(StandardCharsets.UTF_8));
        path.toFile().setExecutable(true);
        return path;
    }

    private static byte[] elf64(int... programHeaderTypes) {
        int headerSize = 64;
        int programHeaderSize = 56;
        ByteBuffer buffer = ByteBuffer
                .allocate(headerSize + programHeaderSize * programHeaderTypes.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 0x7F);
        buffer.put(1, (byte) 'E');
        buffer.put(2, (byte) 'L');
        buffer.put(3, (byte) 'F');
        buffer.put(4, (byte) 2);
        buffer.put(5, (byte) 1);
        buffer.putLong(0x20, headerSize);
        buffer.putShort(0x36, (short) programHeaderSize);
        buffer.putShort(0x38, (short) programHeaderTypes.length);
        for (int index = 0; index < programHeaderTypes.length; index++) {
            buffer.putInt(headerSize + index * programHeaderSize, programHeaderTypes[index]);
        }
        return buffer.array();
    }
}
