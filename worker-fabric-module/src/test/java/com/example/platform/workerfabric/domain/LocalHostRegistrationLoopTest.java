package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** P2-5b-2a-1-2-R3a: registration loop semantics (unit level; no testcontainers). */
class LocalHostRegistrationLoopTest {

    private static final PhysicalHostId HOST_ID = PhysicalHostId.of("bounded-local-host");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void registersHostAndRuntimeWithBoundedValidity() {
        RecordingBoundary boundary = new RecordingBoundary();
        new LocalHostRegistrationLoop(probe(), boundary, CLOCK, HOST_ID).registerOnce();

        assertThat(boundary.hosts).hasSize(1);
        WorkerFabricRegistrationBoundary.HostRegistration host = boundary.hosts.getFirst();
        assertThat(host.physicalHostId()).isEqualTo(HOST_ID);
        assertThat(host.hostResourceSnapshot().physicalHostId()).isEqualTo(HOST_ID);
        assertThat(host.hostResourceSnapshot().snapshotGeneration())
                .isEqualTo(HostResourceSnapshotGeneration.first());
        assertThat(host.hostResourceSnapshot().schemaVersion())
                .isEqualTo(HostResourceSnapshotSchemaVersion.CURRENT);
        assertThat(host.registeredAt()).isEqualTo(CLOCK.instant());
        assertThat(host.validUntil())
                .isEqualTo(CLOCK.instant().plus(LocalHostRegistrationLoop.REGISTRATION_VALIDITY));

        assertThat(boundary.runtimes).hasSize(1);
        WorkerFabricRegistrationBoundary.RuntimeRegistration runtime = boundary.runtimes.getFirst();
        assertThat(runtime.workerRuntimeId().value())
                .isEqualTo(LocalHostRegistrationLoop.WORKER_RUNTIME_ID);
        assertThat(runtime.workerRuntimeIncarnationId().value())
                .isEqualTo(LocalHostRegistrationLoop.WORKER_RUNTIME_INCARNATION_ID);
        assertThat(runtime.physicalHostIncarnationId())
                .isEqualTo(host.physicalHostIncarnationId());
    }

    @Test
    void derivesFloorRoundedSafetyHeadroomWithinCapacity() {
        SafetyHeadroom headroom = LocalHostRegistrationLoop.headroomFor(capacity(2000L, 1000L, 1000L));

        // CPU: max(1 core, 10% of 2000 millicores = 200) -> 1000; memory/temp: floor(10%)
        assertThat(headroom.resources().cpuMillicores()).isEqualTo(1000L);
        assertThat(headroom.resources().memoryBytes()).isEqualTo(100L);
        assertThat(headroom.resources().temporaryStorageBytes()).isEqualTo(100L);
        assertThat(headroom.resources().deviceResources()).isEmpty();

        // Floor rounding with a non-round capacity: 10% of 999 bytes = 99.9 -> 99
        SafetyHeadroom floored = LocalHostRegistrationLoop.headroomFor(capacity(4000L, 999L, 999L));
        assertThat(floored.resources().memoryBytes()).isEqualTo(99L);
        assertThat(floored.resources().temporaryStorageBytes()).isEqualTo(99L);
        assertThat(floored.resources().cpuMillicores()).isEqualTo(1000L);
    }

    @Test
    void incarnationFingerprintIsStableAcrossTicks() {
        RecordingBoundary boundary = new RecordingBoundary();
        LocalHostRegistrationLoop loop = new LocalHostRegistrationLoop(probe(), boundary, CLOCK, HOST_ID);

        loop.registerOnce();
        loop.registerOnce();

        assertThat(boundary.hosts).hasSize(2);
        assertThat(boundary.hosts.get(1).physicalHostIncarnationId())
                .isEqualTo(boundary.hosts.get(0).physicalHostIncarnationId());
        assertThat(boundary.hosts.get(0).physicalHostIncarnationId().value()).hasSize(64);
        // Different static capacity → different incarnation fingerprint.
        assertThat(loop.stableIncarnationFingerprint(capacity(2000L, 1000L, 1000L)))
                .isNotEqualTo(loop.stableIncarnationFingerprint(capacity(4000L, 1000L, 1000L)));
    }

    @Test
    void failsClosedAndRegistersNothingWhenObservationIsUnavailable() {
        RecordingBoundary boundary = new RecordingBoundary();
        HostResourceAgent.ResourceProbe failing = new HostResourceAgent.ResourceProbe() {
            @Override
            public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId id) {
                return new PhysicalHostDescriptor(id, HostLocation.of("local"), TrustZoneId.of("local"), List.of());
            }

            @Override
            public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor descriptor) {
                throw new HostObservationUnavailableException("host evidence unreadable");
            }

            @Override
            public ObservedUsage collectHostAndDeviceObservation(PhysicalHostDescriptor descriptor) {
                throw new HostObservationUnavailableException("host evidence unreadable");
            }
        };

        assertThatThrownBy(() -> new LocalHostRegistrationLoop(failing, boundary, CLOCK, HOST_ID).registerOnce())
                .isInstanceOf(HostObservationUnavailableException.class);
        assertThat(boundary.hosts).isEmpty();
        assertThat(boundary.runtimes).isEmpty();
    }

    // ---------- fixture ----------

    private static HostResourceAgent.ResourceProbe probe() {
        return new HostResourceAgent.ResourceProbe() {
            @Override
            public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId id) {
                return new PhysicalHostDescriptor(id, HostLocation.of("local"), TrustZoneId.of("local"), List.of());
            }

            @Override
            public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor descriptor) {
                return capacity(4000L, 1073741824L, 1073741824L);
            }

            @Override
            public ObservedUsage collectHostAndDeviceObservation(PhysicalHostDescriptor descriptor) {
                return new ObservedUsage(
                        new ObservedCpuUsage(0.25d),
                        new ObservedMemoryUsage(1024L),
                        new ObservedTemporaryStorageUsage(2048L),
                        Map.of());
            }
        };
    }

    private static CapacitySnapshot capacity(long millicores, long memoryBytes, long temporaryBytes) {
        return new CapacitySnapshot(
                CpuCapacity.ofMillicores(millicores),
                MemoryCapacity.ofBytes(memoryBytes),
                TemporaryStorageCapacity.ofBytes(temporaryBytes),
                Map.of());
    }

    private static final class RecordingBoundary implements WorkerFabricRegistrationBoundary {

        private final List<HostRegistration> hosts = new ArrayList<>();
        private final List<RuntimeRegistration> runtimes = new ArrayList<>();

        @Override
        public void registerHost(HostRegistration registration) {
            hosts.add(registration);
        }

        @Override
        public void registerRuntime(RuntimeRegistration registration) {
            runtimes.add(registration);
        }

        @Override
        public Optional<HostResourceSnapshotGeneration> currentSnapshotGeneration(
                PhysicalHostId physicalHostId, PhysicalHostIncarnationId physicalHostIncarnationId) {
            return Optional.empty();
        }
    }
}
