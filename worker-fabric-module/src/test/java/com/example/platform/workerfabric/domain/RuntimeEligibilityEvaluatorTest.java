package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RuntimeEligibilityEvaluatorTest {

    @Test
    void stageTwoFailsClosedWithoutCanonicalHardwareAndRuntimeDependencyEvidence() {
        RequestBuilder builder = new RequestBuilder();
        builder.hardwareObservation = Optional.empty();
        builder.dependencyObservation = Optional.empty();

        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(
                builder.build());

        assertThat(decision.status())
                .isEqualTo(RuntimeEligibilityDecision.Status.UNKNOWN_FAIL_CLOSED);
        assertThat(decision.eligible()).isFalse();
    }

    @Test
    void fullyAvailableStaticallyProvenNativeCandidateIsEligible() {
        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(
                new RequestBuilder().build());

        assertThat(decision.status()).isEqualTo(RuntimeEligibilityDecision.Status.ELIGIBLE);
        assertThat(decision.reasons()).isEmpty();
        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void canonicalConformancePreservesResourceInsufficiencyAndReservationConflict() {
        RequestBuilder insufficient = new RequestBuilder();
        insufficient.resourceDemand = new RuntimeResourceDemand(
                20_000, 128_000, 200_000, insufficient.deviceDemand());
        RequestBuilder conflict = new RequestBuilder();
        conflict.reservationFeasibility = ReservationFeasibility.CONFLICT;

        assertThat(RuntimeEligibilityEvaluator.evaluate(insufficient.build()).reasons())
                .containsExactly(RuntimeEligibilityReason.INSUFFICIENT_CURRENT_RESOURCE);
        assertThat(RuntimeEligibilityEvaluator.evaluate(conflict.build()).reasons())
                .containsExactly(RuntimeEligibilityReason.RESERVATION_CONFLICT);
    }

    @Test
    void matching_support_advertisement_is_only_one_required_candidate_evidence_input() {
        RequestBuilder builder = new RequestBuilder();
        builder.requireAdvertisedSupport();
        builder.capacity = Optional.empty();

        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(builder.build());

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons())
                .contains(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY)
                .doesNotContain(
                        RuntimeEligibilityReason.RUNTIME_SUPPORT_ADVERTISEMENT_MISSING,
                        RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH,
                        RuntimeEligibilityReason.RUNTIME_SUPPORT_UNSUPPORTED);
    }

    @Test
    void missing_unsupported_mismatched_and_requirementless_advertisement_are_rejected() {
        assertSupportRejected(
                builder -> {
                    builder.requireAdvertisedSupport();
                    builder.supportAdvertisement = Optional.empty();
                },
                RuntimeEligibilityReason.RUNTIME_SUPPORT_ADVERTISEMENT_MISSING);
        assertSupportRejected(
                builder -> {
                    builder.requireAdvertisedSupport();
                    builder.supportAdvertisement = Optional.of(new WorkerRuntimeSupportAdvertisement(
                            builder.runtimeId,
                            RuntimeLifecycleKind.EPHEMERAL_TASK,
                            Map.of()));
                },
                RuntimeEligibilityReason.RUNTIME_SUPPORT_UNSUPPORTED);
        assertSupportRejected(
                builder -> {
                    builder.requireAdvertisedSupport();
                    builder.supportAdvertisement = Optional.of(new WorkerRuntimeSupportAdvertisement(
                            WorkerRuntimeId.of("runtime-foreign"),
                            RuntimeLifecycleKind.EPHEMERAL_TASK,
                            Map.of(builder.supportId, builder.supportEvidence)));
                },
                RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH);
        assertSupportRejected(
                builder -> builder.supportAdvertisement = Optional.of(
                        builder.matchingAdvertisement()),
                RuntimeEligibilityReason.RUNTIME_SUPPORT_REQUIREMENT_MISSING);
    }

    @Test
    void completeRuntimeReasonAlgebraIsReachableAndTyped() {
        Map<RuntimeEligibilityReason, Consumer<RequestBuilder>> scenarios =
                new EnumMap<>(RuntimeEligibilityReason.class);
        scenarios.put(RuntimeEligibilityReason.PROBE_UNKNOWN, builder -> {
            builder.probeRequirement = ProviderProbeRequirement.REQUIRED;
            builder.probe = Optional.empty();
        });
        scenarios.put(RuntimeEligibilityReason.RUNTIME_SUPPORT_REQUIREMENT_MISSING,
                builder -> builder.supportAdvertisement = Optional.of(
                        builder.matchingAdvertisement()));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_SUPPORT_ADVERTISEMENT_MISSING, builder -> {
            builder.requireAdvertisedSupport();
            builder.supportAdvertisement = Optional.empty();
        });
        scenarios.put(RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH, builder -> {
            builder.requireAdvertisedSupport();
            builder.supportAdvertisement = Optional.of(new WorkerRuntimeSupportAdvertisement(
                    WorkerRuntimeId.of("runtime-foreign"),
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    Map.of(builder.supportId, builder.supportEvidence)));
        });
        scenarios.put(RuntimeEligibilityReason.RUNTIME_SUPPORT_UNSUPPORTED, builder -> {
            builder.requireAdvertisedSupport();
            builder.supportAdvertisement = Optional.of(new WorkerRuntimeSupportAdvertisement(
                    builder.runtimeId, RuntimeLifecycleKind.EPHEMERAL_TASK, Map.of()));
        });
        scenarios.put(RuntimeEligibilityReason.PROBE_STALE, builder -> {
            builder.probeRequirement = ProviderProbeRequirement.REQUIRED;
            builder.probe = Optional.of(new ProviderProbeResult(
                    builder.scenario.task().providerBindingPin(), ProviderProbeResult.Status.STALE));
        });
        scenarios.put(RuntimeEligibilityReason.PROBE_FAILED, builder -> {
            builder.probeRequirement = ProviderProbeRequirement.REQUIRED;
            builder.probe = Optional.of(new ProviderProbeResult(
                    builder.scenario.task().providerBindingPin(), ProviderProbeResult.Status.FAILED));
        });
        scenarios.put(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER,
                builder -> builder.worker = Optional.empty());
        scenarios.put(RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE,
                builder -> builder.device = Optional.empty());
        scenarios.put(RuntimeEligibilityReason.WORKER_UNAVAILABLE,
                builder -> builder.workerAvailability = Optional.of(new WorkerRuntimeAvailability(
                        builder.runtimeId, builder.runtimeIncarnation, AvailabilityState.UNREACHABLE)));
        scenarios.put(RuntimeEligibilityReason.HOST_UNAVAILABLE,
                builder -> builder.hostAvailability = Optional.of(new PhysicalHostAvailability(
                        builder.hostId, builder.hostIncarnation, AvailabilityState.UNREACHABLE)));
        scenarios.put(RuntimeEligibilityReason.DEVICE_UNAVAILABLE,
                builder -> builder.deviceAvailability = Optional.of(
                        new DeviceAvailability(builder.deviceId, AvailabilityState.UNREACHABLE)));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_UNAVAILABLE,
                builder -> builder.runtimeEnvironment = RuntimeEnvironmentAvailability.UNAVAILABLE);
        scenarios.put(RuntimeEligibilityReason.SANDBOX_RUNTIME_UNAVAILABLE, builder -> {
            builder.sandboxRequirement = SandboxRuntimeRequirement.REQUIRED;
            builder.sandboxAvailability = SandboxRuntimeAvailability.UNAVAILABLE;
        });
        scenarios.put(RuntimeEligibilityReason.INSUFFICIENT_CURRENT_RESOURCE,
                builder -> builder.resourceDemand = new RuntimeResourceDemand(
                        20_000, 128_000, 200_000, builder.deviceDemand()));
        scenarios.put(RuntimeEligibilityReason.STALE_HOST_RESOURCE_SNAPSHOT,
                builder -> builder.snapshot = Optional.of(
                        builder.snapshot(builder.now.minus(Duration.ofMinutes(6)))));
        scenarios.put(RuntimeEligibilityReason.HOST_INCARNATION_MISMATCH,
                builder -> builder.binding = Optional.of(new LocalWorkerRuntimeIncarnationBinding(
                        builder.runtimeId,
                        builder.runtimeIncarnation,
                        builder.hostId,
                        PhysicalHostIncarnationId.of("host-boot-old"))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_INCARNATION_MISMATCH,
                builder -> builder.binding = Optional.of(new LocalWorkerRuntimeIncarnationBinding(
                        builder.runtimeId,
                        WorkerRuntimeIncarnationId.of("runtime-boot-old"),
                        builder.hostId,
                        builder.hostIncarnation)));
        scenarios.put(RuntimeEligibilityReason.RESERVATION_CONFLICT,
                builder -> builder.reservationFeasibility = ReservationFeasibility.CONFLICT);
        scenarios.put(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY,
                builder -> builder.runtimeEnvironment = RuntimeEnvironmentAvailability.UNKNOWN);
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_INCOMPLETE_CRITICAL_EVIDENCE,
                builder -> builder.hardwareObservation = Optional.empty());
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_PROVIDER_IMPLEMENTATION_MISMATCH,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        ProviderImplementationId.of("provider-foreign.native"),
                        builder.runtimeId,
                        builder.hostId,
                        Optional.of(builder.deviceId),
                        I4TestFixture.matchingHardwareEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_WORKER_RUNTIME_MISMATCH,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.providerImplementationId(),
                        WorkerRuntimeId.of("runtime-foreign"),
                        builder.hostId,
                        Optional.of(builder.deviceId),
                        I4TestFixture.matchingHardwareEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_PHYSICAL_HOST_MISMATCH,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        PhysicalHostId.of("host-foreign"),
                        Optional.of(builder.deviceId),
                        I4TestFixture.matchingHardwareEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_IDENTITY_MISMATCH,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        builder.hostId,
                        Optional.of(DeviceId.of("gpu-foreign")),
                        I4TestFixture.matchingHardwareEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_STALE_OBSERVATION,
                builder -> builder.hardwareObservation = Optional.of(I4TestFixture.hardwareObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        builder.hostId,
                        Optional.of(builder.deviceId),
                        builder.now.minus(Duration.ofMinutes(2)),
                        builder.now,
                        I4TestFixture.matchingHardwareEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_PROBE_UNKNOWN,
                builder -> builder.hardwareObservation = Optional.of(
                        builder.hardwareObservation(new ProviderHardwareProbeUnknownEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_PROBE_FAILED,
                builder -> builder.hardwareObservation = Optional.of(
                        builder.hardwareObservation(new ProviderHardwareProbeFailedEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_RUNTIME_UNAVAILABLE,
                builder -> builder.hardwareObservation = Optional.of(
                        builder.hardwareObservation(new ProviderHardwareRuntimeUnavailableEvidence())));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_CPU_ARCHITECTURE_INCOMPATIBLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.AARCH64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                I4TestFixture.matchingHardwareEvidence().deviceEvidence()))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_CLASS_UNAVAILABLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                Optional.empty()))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_UNAVAILABLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                Optional.of(new ProviderHardwareUnavailableDevice())))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DRIVER_RUNTIME_INCOMPATIBLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                Optional.of(builder.availableDevice(
                                        new DriverRuntimeObservation(
                                                RuntimeDependencyVersion.of("11.0"),
                                                Optional.of(RuntimeDependencyAbi.of("driver.12"))),
                                        List.of("tensor.compute")))))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_PROVIDER_BUILD_FEATURE_MISSING,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of(),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                I4TestFixture.matchingHardwareEvidence().deviceEvidence()))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_CODEC_OR_FILTER_FEATURE_MISSING,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of(),
                                List.of("device.access"),
                                I4TestFixture.matchingHardwareEvidence().deviceEvidence()))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_FEATURE_UNAVAILABLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                Optional.of(builder.availableDevice(
                                        new DriverRuntimeObservation(
                                                RuntimeDependencyVersion.of("12.4"),
                                                Optional.of(RuntimeDependencyAbi.of("driver.12"))),
                                        List.of()))))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_DEVICE_NOT_EXPOSED,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of("device.access"),
                                Optional.of(new ProviderHardwareNotExposedDevice())))));
        scenarios.put(RuntimeEligibilityReason.PROVIDER_HARDWARE_SANDBOX_PERMISSION_UNAVAILABLE,
                builder -> builder.hardwareObservation = Optional.of(builder.hardwareObservation(
                        builder.availableHardware(
                                CpuArchitecture.X86_64,
                                List.of("module.gpu"),
                                List.of("codec.h264"),
                                List.of(),
                                I4TestFixture.matchingHardwareEvidence().deviceEvidence()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_INCOMPLETE_CRITICAL_EVIDENCE,
                builder -> builder.dependencyObservation = Optional.empty());
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_PROBE_SCHEMA_MISMATCH,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        Optional.of(builder.deviceId),
                        new RuntimeDependencyProbeSchemaVersion(2),
                        builder.now.minus(Duration.ofMinutes(1)),
                        builder.now.plus(Duration.ofMinutes(1)),
                        List.of(I4TestFixture.matchingObservedDependency()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_PROVIDER_IMPLEMENTATION_MISMATCH,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        ProviderImplementationId.of("provider-foreign.native"),
                        builder.runtimeId,
                        Optional.of(builder.deviceId),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        builder.now.minus(Duration.ofMinutes(1)),
                        builder.now.plus(Duration.ofMinutes(1)),
                        List.of(I4TestFixture.matchingObservedDependency()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_WORKER_RUNTIME_MISMATCH,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        builder.providerImplementationId(),
                        WorkerRuntimeId.of("runtime-foreign"),
                        Optional.of(builder.deviceId),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        builder.now.minus(Duration.ofMinutes(1)),
                        builder.now.plus(Duration.ofMinutes(1)),
                        List.of(I4TestFixture.matchingObservedDependency()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_DEVICE_BINDING_MISMATCH,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        Optional.of(DeviceId.of("gpu-foreign")),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        builder.now.minus(Duration.ofMinutes(1)),
                        builder.now.plus(Duration.ofMinutes(1)),
                        List.of(I4TestFixture.matchingObservedDependency()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_STALE_OBSERVATION,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        builder.providerImplementationId(),
                        builder.runtimeId,
                        Optional.of(builder.deviceId),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        builder.now.minus(Duration.ofMinutes(2)),
                        builder.now,
                        List.of(I4TestFixture.matchingObservedDependency()))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_MISSING,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        RuntimeDependencyProbeSchemaVersion.CURRENT, List.of())));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_VERSION_INCOMPATIBLE,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        List.of(I4TestFixture.observedDependency(
                                "2.0", Optional.of(RuntimeDependencyAbi.of("native.1")),
                                List.of("codec.h264"), List.of("feature.enabled"))))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_ABI_INCOMPATIBLE,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        List.of(I4TestFixture.observedDependency(
                                "1.0", Optional.of(RuntimeDependencyAbi.of("native.2")),
                                List.of("codec.h264"), List.of("feature.enabled"))))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_FEATURE_MISSING,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        List.of(I4TestFixture.observedDependency(
                                "1.0", Optional.of(RuntimeDependencyAbi.of("native.1")),
                                List.of(), List.of("feature.enabled"))))));
        scenarios.put(RuntimeEligibilityReason.RUNTIME_DEPENDENCY_BUILD_RUNTIME_FLAG_MISSING,
                builder -> builder.dependencyObservation = Optional.of(builder.dependencyObservation(
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        List.of(I4TestFixture.observedDependency(
                                "1.0", Optional.of(RuntimeDependencyAbi.of("native.1")),
                                List.of("codec.h264"), List.of())))));

        assertThat(scenarios).hasSize(RuntimeEligibilityReason.values().length);
        scenarios.forEach((expected, mutate) -> {
            RequestBuilder builder = new RequestBuilder();
            mutate.accept(builder);
            RuntimeEligibilityDecision decision =
                    RuntimeEligibilityEvaluator.evaluate(builder.build());
            assertThat(decision.eligible()).as(expected.name()).isFalse();
            assertThat(decision.reasons()).as(expected.name()).contains(expected);
            if (expected.unknownEvidence()) {
                assertThat(decision.status())
                        .as(expected.name())
                        .isEqualTo(RuntimeEligibilityDecision.Status.UNKNOWN_FAIL_CLOSED);
            }
        });
    }

    @Test
    void evaluatorRequiresNativeSelectionAfterExactStageOneProviderLegality() {
        var scenario = TaskBTestFixture.scenario("provider-a", "unit-a");
        ExecutionBackendSelection openCue =
                TaskBTestFixture.selection(scenario, ExecutionBackend.OPEN_CUE_FARM);
        RequestBuilder builder = new RequestBuilder(scenario);
        builder.selection = openCue;

        assertThatIllegalArgumentException().isThrownBy(builder::build)
                .withMessageContaining("selected Native Pull");
    }

    @Test
    void runtimeEligibilityCannotRebindStaticallyProvenProvider() {
        RequestBuilder builder = new RequestBuilder();
        builder.provider = TaskBTestFixture.provider("provider-foreign");

        assertThatIllegalArgumentException().isThrownBy(builder::build)
                .withMessageContaining("cannot rebind");
    }

    @Test
    void runtimeEligibilityRequiresOneExactFeasibilityViewAuthorizedProofPerMembership() {
        RequestBuilder missing = new RequestBuilder();
        missing.staticProofs = List.of();

        assertThatIllegalArgumentException().isThrownBy(missing::build)
                .withMessageContaining("one exact Stage-1 proof per task membership");

        RequestBuilder mismatched = new RequestBuilder();
        var otherUnit = TaskBTestFixture.scenario("provider-a", "unit-foreign");
        mismatched.staticProofs = otherUnit.task().memberships().stream()
                .map(membership -> otherUnit.graph().providerFeasibilityView()
                        .requireStaticallyFeasible(
                                membership.physicalPlanUnit(), otherUnit.provider()))
                .toList();

        assertThatIllegalArgumentException().isThrownBy(mismatched::build)
                .withMessageContaining("does not bind the exact task membership and provider");
    }

    @Test
    void runtimeFailureAlgebraContainsNoStaticCapabilityOrProviderCompatibilityReasons() {
        assertThat(List.of(RuntimeEligibilityReason.values()))
                .extracting(Enum::name)
                .noneMatch(name -> name.contains("CAPABILITY")
                        || name.contains("CONTRACT")
                        || name.contains("DETERMINISM")
                        || name.contains("LOWERING")
                        || name.contains("STATIC_"));
    }

    private static void assertSupportRejected(
            Consumer<RequestBuilder> mutation, RuntimeEligibilityReason expected) {
        RequestBuilder builder = new RequestBuilder();
        mutation.accept(builder);
        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(builder.build());
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasons()).contains(expected);
    }

    private static final class RequestBuilder {

        private final TaskBTestFixture.Scenario scenario;
        private final Instant now = Instant.parse("2026-08-24T12:00:00Z");
        private final PhysicalHostId hostId = PhysicalHostId.of("host-a");
        private final PhysicalHostIncarnationId hostIncarnation =
                PhysicalHostIncarnationId.of("host-boot-1");
        private final WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-a");
        private final WorkerRuntimeIncarnationId runtimeIncarnation =
                WorkerRuntimeIncarnationId.of("runtime-boot-1");
        private final DeviceId deviceId = DeviceId.of("gpu-0");
        private final DeviceDescriptor deviceDescriptor = new DeviceDescriptor(
                deviceId,
                DeviceKind.GPU,
                DeviceVendor.of("vendor"),
                DeviceModel.of("model"));
        private final PhysicalHostDescriptor hostDescriptor = new PhysicalHostDescriptor(
                hostId,
                HostLocation.of("region-a"),
                TrustZoneId.of("trusted"),
                List.of(deviceDescriptor));

        private com.example.platform.execution.compatibility.ProviderCandidate provider;
        private List<StaticProviderCompatibilityProof> staticProofs;
        private ProviderHardwareRequirement hardwareRequirement;
        private List<RuntimeDependencyRequirement> dependencyRequirements;
        private Optional<ProviderHardwareObservation> hardwareObservation;
        private Optional<RuntimeDependencyObservation> dependencyObservation;
        private ExecutionBackendSelection selection;
        private Optional<WorkerRuntimeDescriptor> worker;
        private Optional<WorkerRuntimeAvailability> workerAvailability;
        private Optional<LocalWorkerRuntimeIncarnationBinding> binding;
        private Optional<PhysicalHostDescriptor> host;
        private Optional<PhysicalHostAvailability> hostAvailability;
        private Optional<HostResourceSnapshot> snapshot;
        private Optional<DeviceDescriptor> device;
        private Optional<DeviceAvailability> deviceAvailability;
        private Optional<SchedulableCapacity> capacity;
        private RuntimeResourceDemand resourceDemand;
        private ReservationFeasibility reservationFeasibility = ReservationFeasibility.FEASIBLE;
        private RuntimeEnvironmentAvailability runtimeEnvironment =
                RuntimeEnvironmentAvailability.AVAILABLE;
        private SandboxRuntimeRequirement sandboxRequirement = SandboxRuntimeRequirement.NOT_REQUIRED;
        private SandboxRuntimeAvailability sandboxAvailability = SandboxRuntimeAvailability.AVAILABLE;
        private ProviderProbeRequirement probeRequirement = ProviderProbeRequirement.NOT_REQUIRED;
        private Optional<ProviderProbeResult> probe;
        private final RuntimeSupportIdentifier supportId =
                RuntimeSupportIdentifier.of("ffmpeg.cpu.transcode.v1");
        private final RuntimeSupportEvidence supportEvidence =
                new RuntimeSupportEvidence("provider-module", "ffmpeg-provider-module:v1");
        private Optional<WorkerRuntimeSupportAdvertisement> supportAdvertisement = Optional.empty();
        private Optional<WorkerRuntimeSupportRequirement> supportRequirement = Optional.empty();

        private RequestBuilder() {
            this(TaskBTestFixture.scenario("provider-a", "unit-a"));
        }

        private RequestBuilder(TaskBTestFixture.Scenario scenario) {
            this.scenario = scenario;
            provider = scenario.provider();
            ProviderImplementationId providerImplementationId =
                    provider.descriptor().providerImplementationId();
            staticProofs = scenario.task().memberships().stream()
                    .map(membership -> scenario.graph().providerFeasibilityView()
                            .requireStaticallyFeasible(
                                    membership.physicalPlanUnit(), provider))
                    .toList();
            hardwareRequirement = I4TestFixture.hardwareRequirement(providerImplementationId);
            dependencyRequirements =
                    List.of(I4TestFixture.dependencyRequirement(providerImplementationId));
            hardwareObservation = Optional.of(I4TestFixture.hardwareObservation(
                    providerImplementationId,
                    runtimeId,
                    hostId,
                    Optional.of(deviceId),
                    now));
            dependencyObservation = Optional.of(I4TestFixture.dependencyObservation(
                    providerImplementationId,
                    runtimeId,
                    Optional.of(deviceId),
                    now));
            selection = TaskBTestFixture.selection(scenario, ExecutionBackend.NATIVE_PULL_WORKER);
            worker = Optional.of(WorkerRuntimeDescriptor.local(
                    runtimeId, RuntimeLifecycleKind.EPHEMERAL_TASK, hostId));
            workerAvailability = Optional.of(new WorkerRuntimeAvailability(
                    runtimeId, runtimeIncarnation, AvailabilityState.REACHABLE));
            binding = Optional.of(new LocalWorkerRuntimeIncarnationBinding(
                    runtimeId, runtimeIncarnation, hostId, hostIncarnation));
            host = Optional.of(hostDescriptor);
            hostAvailability = Optional.of(new PhysicalHostAvailability(
                    hostId, hostIncarnation, AvailabilityState.REACHABLE));
            snapshot = Optional.of(snapshot(now));
            device = Optional.of(deviceDescriptor);
            deviceAvailability = Optional.of(
                    new DeviceAvailability(deviceId, AvailabilityState.REACHABLE));
            capacity = Optional.of(new SchedulableCapacity(
                    hostId,
                    hostIncarnation,
                    SchedulableCapacityDisposition.AVAILABLE,
                    CpuCapacity.ofMillicores(8_000),
                    MemoryCapacity.ofBytes(64_000),
                    TemporaryStorageCapacity.ofBytes(100_000),
                    Map.of(deviceId, new DeviceResourceCapacity(deviceId, 16_000, 100, 4, 4))));
            resourceDemand = new RuntimeResourceDemand(
                    1_000, 1_000, 1_000, deviceDemand());
            probe = Optional.of(new ProviderProbeResult(
                    scenario.task().providerBindingPin(), ProviderProbeResult.Status.HEALTHY));
        }

        private NativeRuntimeEligibilityRequest build() {
            return new NativeRuntimeEligibilityRequest(
                    scenario.graph(),
                    scenario.task(),
                    provider,
                    staticProofs,
                    hardwareRequirement,
                    dependencyRequirements,
                    hardwareObservation,
                    dependencyObservation,
                    selection,
                    worker,
                    workerAvailability,
                    binding,
                    host,
                    hostAvailability,
                    snapshot,
                    new HostResourceSnapshotFreshnessPolicy(
                            Duration.ofMinutes(5), HostResourceSnapshotSchemaVersion.CURRENT),
                    now,
                    device,
                    deviceAvailability,
                    capacity,
                    resourceDemand,
                    reservationFeasibility,
                    runtimeEnvironment,
                    sandboxRequirement,
                    sandboxAvailability,
                    supportAdvertisement,
                    supportRequirement,
                    probeRequirement,
                    probe);
        }

        private void requireAdvertisedSupport() {
            supportAdvertisement = Optional.of(matchingAdvertisement());
            supportRequirement = Optional.of(new WorkerRuntimeSupportRequirement(
                    scenario.task().providerBindingPin(),
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    supportId));
        }

        private WorkerRuntimeSupportAdvertisement matchingAdvertisement() {
            return new WorkerRuntimeSupportAdvertisement(
                    runtimeId,
                    RuntimeLifecycleKind.EPHEMERAL_TASK,
                    Map.of(supportId, supportEvidence));
        }

        private Map<DeviceId, RuntimeResourceDemand.DeviceDemand> deviceDemand() {
            return Map.of(deviceId, new RuntimeResourceDemand.DeviceDemand(
                    deviceId, 1_000, 10, 1, 1));
        }

        private ProviderImplementationId providerImplementationId() {
            return scenario.provider().descriptor().providerImplementationId();
        }

        private ProviderHardwareObservation hardwareObservation(
                ProviderHardwareProbeEvidence evidence) {
            return hardwareObservation(
                    providerImplementationId(), runtimeId, hostId, Optional.of(deviceId), evidence);
        }

        private ProviderHardwareObservation hardwareObservation(
                ProviderImplementationId providerImplementationId,
                WorkerRuntimeId observationRuntimeId,
                PhysicalHostId observationHostId,
                Optional<DeviceId> observationDeviceId,
                ProviderHardwareProbeEvidence evidence) {
            return I4TestFixture.hardwareObservation(
                    providerImplementationId,
                    observationRuntimeId,
                    observationHostId,
                    observationDeviceId,
                    now.minus(Duration.ofMinutes(1)),
                    now.plus(Duration.ofMinutes(1)),
                    evidence);
        }

        private ProviderHardwareAvailableEvidence availableHardware(
                CpuArchitecture cpuArchitecture,
                List<String> buildFeatures,
                List<String> codecFeatures,
                List<String> sandboxPermissions,
                Optional<ProviderHardwareDeviceEvidence> deviceEvidence) {
            return new ProviderHardwareAvailableEvidence(
                    cpuArchitecture,
                    buildFeatures,
                    codecFeatures,
                    sandboxPermissions,
                    deviceEvidence);
        }

        private ProviderHardwareAvailableDevice availableDevice(
                DriverRuntimeObservation driverRuntime,
                List<String> availableFeatures) {
            return new ProviderHardwareAvailableDevice(
                    DeviceKind.GPU,
                    DeviceVendor.of("vendor"),
                    DeviceModel.of("model"),
                    driverRuntime,
                    availableFeatures);
        }

        private RuntimeDependencyObservation dependencyObservation(
                RuntimeDependencyProbeSchemaVersion schemaVersion,
                List<RuntimeDependencyObservedDependency> dependencies) {
            return dependencyObservation(
                    providerImplementationId(),
                    runtimeId,
                    Optional.of(deviceId),
                    schemaVersion,
                    now.minus(Duration.ofMinutes(1)),
                    now.plus(Duration.ofMinutes(1)),
                    dependencies);
        }

        private RuntimeDependencyObservation dependencyObservation(
                ProviderImplementationId providerImplementationId,
                WorkerRuntimeId observationRuntimeId,
                Optional<DeviceId> observationDeviceId,
                RuntimeDependencyProbeSchemaVersion schemaVersion,
                Instant observedAt,
                Instant expiresAt,
                List<RuntimeDependencyObservedDependency> dependencies) {
            return I4TestFixture.dependencyObservation(
                    providerImplementationId,
                    observationRuntimeId,
                    observationDeviceId,
                    schemaVersion,
                    observedAt,
                    expiresAt,
                    dependencies);
        }

        private HostResourceSnapshot snapshot(Instant capturedAt) {
            return new HostResourceSnapshot(
                    hostId,
                    hostIncarnation,
                    HostResourceSnapshotGeneration.first(),
                    capturedAt,
                    HostResourceSnapshotSchemaVersion.CURRENT,
                    new CapacitySnapshot(
                            CpuCapacity.ofMillicores(8_000),
                            MemoryCapacity.ofBytes(64_000),
                            TemporaryStorageCapacity.ofBytes(100_000),
                            Map.of(deviceId, new DeviceResourceCapacity(
                                    deviceId, 16_000, 100, 4, 4))),
                    new ObservedUsage(
                            new ObservedCpuUsage(0.1),
                            new ObservedMemoryUsage(1_000),
                            new ObservedTemporaryStorageUsage(1_000),
                            Map.of(deviceId, new ObservedDeviceUsage(
                                    deviceId, 0.1, 1_000, 0.1, 0.1))),
                    Optional.empty());
        }
    }
}
