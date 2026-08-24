package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HostResourceAgentTest {

    private static final PhysicalHostId HOST_ID = PhysicalHostId.of("host-1");
    private static final PhysicalHostIncarnationId BOOT_1 =
            PhysicalHostIncarnationId.of("host-boot-1");
    private static final PhysicalHostIncarnationId BOOT_2 =
            PhysicalHostIncarnationId.of("host-boot-2");
    private static final DeviceId DEVICE_ID = DeviceId.of("gpu-0");
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void agentFingerprintsObservesPublishesAndTracksGenerationPerHostIncarnation() {
        List<HostResourceSnapshot> published = new ArrayList<>();
        HostResourceAgent agent = new HostResourceAgent(
                HOST_ID, probe(), noDurableGeneration(), published::add);

        HostResourceSnapshot first = agent.capture(
                reachable(BOOT_1), CAPTURED_AT, Optional.of(reporterBinding(BOOT_1)));
        HostResourceSnapshot second = agent.capture(
                reachable(BOOT_1), CAPTURED_AT.plusSeconds(1), Optional.empty());
        HostResourceSnapshot afterRestart = agent.capture(
                reachable(BOOT_2), CAPTURED_AT.plusSeconds(2), Optional.of(reporterBinding(BOOT_2)));
        HostResourceSnapshot sameIncarnationAgain = agent.capture(
                reachable(BOOT_1), CAPTURED_AT.plusSeconds(3), Optional.empty());

        assertThat(first.snapshotGeneration()).isEqualTo(HostResourceSnapshotGeneration.first());
        assertThat(second.snapshotGeneration()).isEqualTo(new HostResourceSnapshotGeneration(2));
        assertThat(afterRestart.snapshotGeneration()).isEqualTo(HostResourceSnapshotGeneration.first());
        assertThat(sameIncarnationAgain.snapshotGeneration())
                .isEqualTo(new HostResourceSnapshotGeneration(3));
        assertThat(published).containsExactly(first, second, afterRestart, sameIncarnationAgain);
        assertThat(first.reportingRuntime()).get().satisfies(reporter -> {
            assertThat(reporter.physicalHostId()).isEqualTo(HOST_ID);
            assertThat(reporter.physicalHostIncarnationId()).isEqualTo(BOOT_1);
        });
    }

    @Test
    void reportingRuntimeMustBindCurrentHostIncarnation() {
        HostResourceAgent agent = new HostResourceAgent(
                HOST_ID, probe(), noDurableGeneration(), ignored -> {});

        assertThatIllegalArgumentException().isThrownBy(() -> agent.capture(
                reachable(BOOT_2), CAPTURED_AT, Optional.of(reporterBinding(BOOT_1))));
    }

    @Test
    void reportingRuntimeIsOptionalProvenanceAndNeverCapacityOwner() {
        HostResourceSnapshot withoutReporter = new HostResourceAgent(
                        HOST_ID, probe(), noDurableGeneration(), ignored -> {})
                .capture(reachable(BOOT_1), CAPTURED_AT, Optional.empty());

        assertThat(withoutReporter.reportingRuntime()).isEmpty();
        assertThat(CapacitySnapshot.class.getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType())
                        .isNotEqualTo(WorkerRuntimeId.class));
    }

    @Test
    void probeDeviceEvidenceMustRemainInExactHostInventoryScope() {
        DeviceId foreignDevice = DeviceId.of("foreign-gpu");
        HostResourceAgent.ResourceProbe invalidProbe = new HostResourceAgent.ResourceProbe() {
            @Override
            public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId hostId) {
                return descriptor();
            }

            @Override
            public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor ignored) {
                return new CapacitySnapshot(
                        CpuCapacity.ofMillicores(8_000),
                        MemoryCapacity.ofBytes(64_000),
                        TemporaryStorageCapacity.ofBytes(100_000),
                        Map.of(foreignDevice, DeviceResourceCapacity.none(foreignDevice)));
            }

            @Override
            public ObservedUsage collectHostAndDeviceObservation(PhysicalHostDescriptor ignored) {
                return usage(Map.of(
                        foreignDevice,
                        new ObservedDeviceUsage(foreignDevice, 0.0, 0, 0.0, 0.0)));
            }
        };

        HostResourceAgent agent = new HostResourceAgent(
                HOST_ID, invalidProbe, noDurableGeneration(), ignored -> {});

        assertThatIllegalArgumentException().isThrownBy(() ->
                agent.capture(reachable(BOOT_1), CAPTURED_AT, Optional.empty()));
    }

    @Test
    void agentRejectsEvidenceForAnotherStableHost() {
        HostResourceAgent agent = new HostResourceAgent(
                HOST_ID, probe(), noDurableGeneration(), ignored -> {});
        PhysicalHostAvailability anotherHost = new PhysicalHostAvailability(
                PhysicalHostId.of("host-2"), BOOT_1, AvailabilityState.REACHABLE);

        assertThatIllegalArgumentException().isThrownBy(() ->
                agent.capture(anotherHost, CAPTURED_AT, Optional.empty()));
    }

    private static HostResourceAgent.ResourceProbe probe() {
        return new HostResourceAgent.ResourceProbe() {
            @Override
            public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId hostId) {
                assertThat(hostId).isEqualTo(HOST_ID);
                return descriptor();
            }

            @Override
            public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor hostDescriptor) {
                assertThat(hostDescriptor.id()).isEqualTo(HOST_ID);
                return new CapacitySnapshot(
                        CpuCapacity.ofMillicores(8_000),
                        MemoryCapacity.ofBytes(64_000),
                        TemporaryStorageCapacity.ofBytes(100_000),
                        Map.of(DEVICE_ID, new DeviceResourceCapacity(DEVICE_ID, 16_000, 100, 2, 2)));
            }

            @Override
            public ObservedUsage collectHostAndDeviceObservation(
                    PhysicalHostDescriptor hostDescriptor) {
                assertThat(hostDescriptor.devices()).extracting(DeviceDescriptor::id).containsExactly(DEVICE_ID);
                return usage(Map.of(
                        DEVICE_ID,
                        new ObservedDeviceUsage(DEVICE_ID, 0.1, 1_000, 0.0, 0.0)));
            }
        };
    }

    private static HostResourceAgent.DurableGenerationLookup noDurableGeneration() {
        return (ignoredHost, ignoredIncarnation) -> Optional.empty();
    }

    private static PhysicalHostDescriptor descriptor() {
        return new PhysicalHostDescriptor(
                HOST_ID,
                HostLocation.of("region-a/zone-1"),
                TrustZoneId.of("trusted"),
                List.of(new DeviceDescriptor(
                        DEVICE_ID,
                        DeviceKind.GPU,
                        DeviceVendor.of("vendor"),
                        DeviceModel.of("model"))));
    }

    private static ObservedUsage usage(Map<DeviceId, ObservedDeviceUsage> deviceUsage) {
        return new ObservedUsage(
                new ObservedCpuUsage(0.1),
                new ObservedMemoryUsage(1_000),
                new ObservedTemporaryStorageUsage(1_000),
                deviceUsage);
    }

    private static PhysicalHostAvailability reachable(
            PhysicalHostIncarnationId incarnationId) {
        return new PhysicalHostAvailability(HOST_ID, incarnationId, AvailabilityState.REACHABLE);
    }

    private static LocalWorkerRuntimeIncarnationBinding reporterBinding(
            PhysicalHostIncarnationId hostIncarnationId) {
        return new LocalWorkerRuntimeIncarnationBinding(
                WorkerRuntimeId.of("runtime-reporter"),
                WorkerRuntimeIncarnationId.of("runtime-reporter-incarnation"),
                HOST_ID,
                hostIncarnationId);
    }
}
