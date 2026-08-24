package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
    void fullyAvailableStaticallyProvenNativeCandidateIsEligible() {
        RuntimeEligibilityDecision decision = RuntimeEligibilityEvaluator.evaluate(
                new RequestBuilder().build());

        assertThat(decision.status()).isEqualTo(RuntimeEligibilityDecision.Status.ELIGIBLE);
        assertThat(decision.reasons()).isEmpty();
        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void completeSixteenReasonAlgebraIsReachableAndTyped() {
        Map<RuntimeEligibilityReason, Consumer<RequestBuilder>> scenarios =
                new EnumMap<>(RuntimeEligibilityReason.class);
        scenarios.put(RuntimeEligibilityReason.PROBE_UNKNOWN, builder -> {
            builder.probeRequirement = ProviderProbeRequirement.REQUIRED;
            builder.probe = Optional.empty();
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

        assertThat(scenarios).hasSize(RuntimeEligibilityReason.values().length);
        scenarios.forEach((expected, mutate) -> {
            RequestBuilder builder = new RequestBuilder();
            mutate.accept(builder);
            RuntimeEligibilityDecision decision =
                    RuntimeEligibilityEvaluator.evaluate(builder.build());
            assertThat(decision.eligible()).as(expected.name()).isFalse();
            assertThat(decision.reasons()).as(expected.name()).contains(expected);
            if (expected == RuntimeEligibilityReason.PROBE_UNKNOWN
                    || expected == RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY) {
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
    void runtimeFailureAlgebraContainsNoStaticCapabilityOrProviderCompatibilityReasons() {
        assertThat(List.of(RuntimeEligibilityReason.values()))
                .extracting(Enum::name)
                .noneMatch(name -> name.contains("CAPABILITY")
                        || name.contains("CONTRACT")
                        || name.contains("CODEC")
                        || name.contains("DETERMINISM")
                        || name.contains("LOWERING"));
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

        private RequestBuilder() {
            this(TaskBTestFixture.scenario("provider-a", "unit-a"));
        }

        private RequestBuilder(TaskBTestFixture.Scenario scenario) {
            this.scenario = scenario;
            provider = scenario.provider();
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
                    probeRequirement,
                    probe);
        }

        private Map<DeviceId, RuntimeResourceDemand.DeviceDemand> deviceDemand() {
            return Map.of(deviceId, new RuntimeResourceDemand.DeviceDemand(
                    deviceId, 1_000, 10, 1, 1));
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
