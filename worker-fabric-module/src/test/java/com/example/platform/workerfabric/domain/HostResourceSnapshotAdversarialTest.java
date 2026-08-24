package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HostResourceSnapshotAdversarialTest {

    private static final PhysicalHostId HOST_A = PhysicalHostId.of("host-a");
    private static final PhysicalHostId HOST_B = PhysicalHostId.of("host-b");
    private static final PhysicalHostIncarnationId HOST_A_BOOT_1 =
            PhysicalHostIncarnationId.of("host-a-boot-1");
    private static final PhysicalHostIncarnationId HOST_A_BOOT_2 =
            PhysicalHostIncarnationId.of("host-a-boot-2");
    private static final DeviceId DEVICE_ID = DeviceId.of("gpu-0");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final HostResourceSnapshotFreshnessPolicy FRESHNESS_POLICY =
            new HostResourceSnapshotFreshnessPolicy(
                    Duration.ofMinutes(5), HostResourceSnapshotSchemaVersion.CURRENT);

    @Test
    void h1SnapshotExactHostMatchesSchedulableHostAccepted() {
        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of());

        assertThat(result.available()).isTrue();
        assertThat(result.physicalHostId()).isEqualTo(HOST_A);
        assertThat(result.physicalHostIncarnationId()).isEqualTo(HOST_A_BOOT_1);
    }

    @Test
    void h2SnapshotHostAAndAvailabilityHostBRejected() {
        HostResourceSnapshot hostASnapshot =
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        PhysicalHostAvailability hostB = availability(
                HOST_B, PhysicalHostIncarnationId.of("host-b-boot-1"), AvailabilityState.REACHABLE);

        assertThatIllegalArgumentException().isThrownBy(() ->
                hostCapacity(hostASnapshot, hostB, List.of()));
    }

    @Test
    void h3SameHostIdWithStaleHostIncarnationRejected() {
        HostResourceSnapshot stale = snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        PhysicalHostAvailability current =
                availability(HOST_A, HOST_A_BOOT_2, AvailabilityState.REACHABLE);

        assertThatIllegalArgumentException().isThrownBy(() ->
                hostCapacity(stale, current, List.of()));
    }

    @Test
    void h4HostRestartInvalidatesOldSnapshotForAssignment() {
        HostResourceSnapshot beforeRestart =
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        HostResourceSnapshot afterRestart =
                snapshot(HOST_A, HOST_A_BOOT_2, NOW, lowUsage());
        PhysicalHostAvailability current =
                availability(HOST_A, HOST_A_BOOT_2, AvailabilityState.REACHABLE);

        assertThatIllegalArgumentException().isThrownBy(() ->
                hostCapacity(beforeRestart, current, List.of()));
        assertThat(hostCapacity(afterRestart, current, List.of()).available()).isTrue();
    }

    @Test
    void h5RuntimeRestartInvalidatesOldRuntimeIncarnationContext() {
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-1");
        WorkerRuntimeIncarnationId oldIncarnation =
                WorkerRuntimeIncarnationId.of("runtime-incarnation-1");
        WorkerRuntimeIncarnationId currentIncarnation =
                WorkerRuntimeIncarnationId.of("runtime-incarnation-2");
        LocalWorkerRuntimeIncarnationBinding staleBinding = binding(runtimeId, oldIncarnation);
        WorkerRuntimeAvailability currentRuntime = new WorkerRuntimeAvailability(
                runtimeId, currentIncarnation, AvailabilityState.REACHABLE);

        assertThatIllegalArgumentException().isThrownBy(() -> localCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                currentRuntime,
                staleBinding,
                descriptor(runtimeId),
                List.of()));
    }

    @Test
    void h6TwoWorkerRuntimesOnOneHostDoNotDoubleHostCapacity() {
        HostResourceSnapshot snapshot = snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        SchedulableCapacity first = localCapacityFor("runtime-1", snapshot, List.of());
        SchedulableCapacity second = localCapacityFor("runtime-2", snapshot, List.of());

        assertThat(first).isEqualTo(second);
        assertThat(new HashSet<>(List.of(first, second))).hasSize(1);
        assertThat(first.cpu().millicores()).isEqualTo(8_000);
        assertThat(Arrays.stream(SchedulableCapacity.class.getRecordComponents())
                        .map(component -> component.getType().getSimpleName()))
                .doesNotContain("WorkerRuntimeId", "WorkerRuntimeIncarnationId");
    }

    @Test
    void h7OneDeviceVisibleToTwoRuntimesIsNotDuplicated() {
        HostResourceSnapshot snapshot = snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        List<SchedulableCapacity> runtimeViews = List.of(
                localCapacityFor("runtime-1", snapshot, List.of()),
                localCapacityFor("runtime-2", snapshot, List.of()));
        Map<DeviceId, DeviceResourceCapacity> canonicalHostDevices = runtimeViews.stream()
                .flatMap(view -> view.deviceResources().entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> {
                            assertThat(right).isEqualTo(left);
                            return left;
                        }));

        assertThat(canonicalHostDevices).containsOnlyKeys(DEVICE_ID);
        assertThat(canonicalHostDevices.get(DEVICE_ID).computeUnits()).isEqualTo(100);
    }

    @Test
    void h8ReservationForExactDeviceRemainsOneHostScopedCommitment() {
        Reservation reservation = reservation(
                "device-reservation", 0, 0, 0, 40, ReservationState.ACTIVE);
        HostResourceSnapshot snapshot = snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage());
        SchedulableCapacity first = localCapacityFor("runtime-1", snapshot, List.of(reservation));
        SchedulableCapacity second = localCapacityFor("runtime-2", snapshot, List.of(reservation));

        assertThat(first).isEqualTo(second);
        assertThat(first.deviceResources()).hasSize(1);
        assertThat(first.deviceResources().get(DEVICE_ID).computeUnits()).isEqualTo(60);
    }

    @Test
    void h9LowObservedUsageDoesNotOverrideLargeCentralReservation() {
        Reservation reservation = reservation(
                "large-reservation", 6_000, 48_000, 75_000, 80, ReservationState.ACTIVE);

        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of(reservation));

        assertThat(result.cpu().millicores()).isEqualTo(2_000);
        assertThat(result.memory().bytes()).isEqualTo(16_000);
        assertThat(result.deviceResources().get(DEVICE_ID).computeUnits()).isEqualTo(20);
    }

    @Test
    void h10HighObservedUsageDoesNotRedefineStaticCapacity() {
        ObservedUsage highUsage = new ObservedUsage(
                new ObservedCpuUsage(1.0),
                new ObservedMemoryUsage(63_999),
                new ObservedTemporaryStorageUsage(99_999),
                Map.of(DEVICE_ID, new ObservedDeviceUsage(DEVICE_ID, 1.0, 15_999, 1.0, 1.0)));

        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, highUsage),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of());

        assertThat(result.available()).isTrue();
        assertThat(result.cpu().millicores()).isEqualTo(8_000);
        assertThat(result.memory().bytes()).isEqualTo(64_000);
        assertThat(result.deviceResources().get(DEVICE_ID).computeUnits()).isEqualTo(100);
    }

    @Test
    void h11UnreachableHostHasZeroSchedulableCapacity() {
        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.UNREACHABLE),
                List.of());

        assertUnavailable(result);
        assertThat(result.disposition()).isEqualTo(SchedulableCapacityDisposition.NO_ASSIGNMENT);
    }

    @Test
    void h12UnreachableRuntimeCannotAuthorizeNativeCapacity() {
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-1");
        WorkerRuntimeIncarnationId incarnation =
                WorkerRuntimeIncarnationId.of("runtime-incarnation-1");
        WorkerRuntimeAvailability unreachable = new WorkerRuntimeAvailability(
                runtimeId, incarnation, AvailabilityState.UNREACHABLE);

        SchedulableCapacity result = localCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                unreachable,
                binding(runtimeId, incarnation),
                descriptor(runtimeId),
                List.of());

        assertUnavailable(result);
        assertThat(result.disposition()).isEqualTo(SchedulableCapacityDisposition.NO_ASSIGNMENT);
    }

    @Test
    void h13StaleSnapshotRequiresReprobeAndCannotAuthorizeAssignment() {
        HostResourceSnapshot stale =
                snapshot(HOST_A, HOST_A_BOOT_1, NOW.minus(Duration.ofMinutes(6)), lowUsage());
        PhysicalHostAvailability current =
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE);

        HostResourceSnapshotFreshness freshness =
                FRESHNESS_POLICY.assess(Optional.of(stale), current, NOW);
        SchedulableCapacity result = hostCapacity(stale, current, List.of());

        assertThat(freshness.status())
                .isEqualTo(HostResourceSnapshotFreshnessStatus.REPROBE_REQUIRED);
        assertThat(freshness.permitsAssignment()).isFalse();
        assertUnavailable(result);
        assertThat(result.disposition()).isEqualTo(SchedulableCapacityDisposition.REPROBE_REQUIRED);
    }

    @Test
    void h14UnknownCriticalFreshnessFailsClosed() {
        HostResourceSnapshotFreshness freshness = FRESHNESS_POLICY.assess(
                Optional.empty(),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                NOW);

        assertThat(freshness.status()).isEqualTo(HostResourceSnapshotFreshnessStatus.FAIL_CLOSED);
        assertThat(freshness.permitsAssignment()).isFalse();
    }

    @Test
    void crossHostReservationScopeFailsClosed() {
        Reservation wrongHostReservation = new Reservation(
                ReservationId.of("wrong-host"),
                HOST_B,
                ReservationKind.TASK,
                ReservedResources.none(),
                ReservationState.ACTIVE);

        assertThatIllegalArgumentException().isThrownBy(() -> hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of(wrongHostReservation)));
    }

    @Test
    void nakedCapacitySnapshotCannotEstablishSchedulability() {
        List<Method> entrypoints = Arrays.stream(SchedulableCapacity.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType().equals(SchedulableCapacity.class))
                .toList();

        assertThat(entrypoints).hasSize(2);
        assertThat(entrypoints).allSatisfy(method -> {
            assertThat(method.getParameterTypes()).contains(HostResourceSnapshot.class);
            assertThat(method.getParameterTypes()).doesNotContain(CapacitySnapshot.class);
        });
    }

    @Test
    void contradictoryObservationFailsClosedForReconciliationWithoutMintingCapacity() {
        ObservedUsage impossibleUsage = new ObservedUsage(
                new ObservedCpuUsage(1.0),
                new ObservedMemoryUsage(64_001),
                new ObservedTemporaryStorageUsage(1),
                Map.of(DEVICE_ID, new ObservedDeviceUsage(DEVICE_ID, 1.0, 1, 1.0, 1.0)));

        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, impossibleUsage),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of());

        assertUnavailable(result);
        assertThat(result.disposition())
                .isEqualTo(SchedulableCapacityDisposition.RECONCILIATION_REQUIRED);
    }

    @Test
    void centralCommitmentBeyondStaticCapacityFailsClosedForReconciliation() {
        Reservation overcommitted = reservation(
                "overcommitted", 8_001, 0, 0, 0, ReservationState.ACTIVE);

        SchedulableCapacity result = hostCapacity(
                snapshot(HOST_A, HOST_A_BOOT_1, NOW, lowUsage()),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                List.of(overcommitted));

        assertUnavailable(result);
        assertThat(result.disposition())
                .isEqualTo(SchedulableCapacityDisposition.RECONCILIATION_REQUIRED);
    }

    private static SchedulableCapacity localCapacityFor(
            String runtimeValue,
            HostResourceSnapshot snapshot,
            List<Reservation> reservations) {
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of(runtimeValue);
        WorkerRuntimeIncarnationId incarnation =
                WorkerRuntimeIncarnationId.of(runtimeValue + "-incarnation-1");
        return localCapacity(
                snapshot,
                new WorkerRuntimeAvailability(runtimeId, incarnation, AvailabilityState.REACHABLE),
                binding(runtimeId, incarnation),
                descriptor(runtimeId),
                reservations);
    }

    private static SchedulableCapacity localCapacity(
            HostResourceSnapshot snapshot,
            WorkerRuntimeAvailability runtimeAvailability,
            LocalWorkerRuntimeIncarnationBinding binding,
            WorkerRuntimeDescriptor descriptor,
            List<Reservation> reservations) {
        return SchedulableCapacity.forLocalRuntime(
                snapshot,
                reservations,
                SafetyHeadroom.none(),
                availability(HOST_A, HOST_A_BOOT_1, AvailabilityState.REACHABLE),
                runtimeAvailability,
                binding,
                descriptor,
                FRESHNESS_POLICY,
                NOW);
    }

    private static SchedulableCapacity hostCapacity(
            HostResourceSnapshot snapshot,
            PhysicalHostAvailability availability,
            List<Reservation> reservations) {
        return SchedulableCapacity.forHost(
                snapshot,
                reservations,
                SafetyHeadroom.none(),
                availability,
                FRESHNESS_POLICY,
                NOW);
    }

    private static HostResourceSnapshot snapshot(
            PhysicalHostId hostId,
            PhysicalHostIncarnationId incarnationId,
            Instant capturedAt,
            ObservedUsage usage) {
        return new HostResourceSnapshot(
                hostId,
                incarnationId,
                HostResourceSnapshotGeneration.first(),
                capturedAt,
                HostResourceSnapshotSchemaVersion.CURRENT,
                staticCapacity(),
                usage,
                Optional.empty());
    }

    private static CapacitySnapshot staticCapacity() {
        return new CapacitySnapshot(
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000),
                TemporaryStorageCapacity.ofBytes(100_000),
                Map.of(DEVICE_ID, new DeviceResourceCapacity(DEVICE_ID, 16_000, 100, 2, 2)));
    }

    private static ObservedUsage lowUsage() {
        return new ObservedUsage(
                new ObservedCpuUsage(0.01),
                new ObservedMemoryUsage(1),
                new ObservedTemporaryStorageUsage(1),
                Map.of(DEVICE_ID, new ObservedDeviceUsage(DEVICE_ID, 0.01, 1, 0.0, 0.0)));
    }

    private static Reservation reservation(
            String id,
            long cpu,
            long memory,
            long storage,
            long deviceCompute,
            ReservationState state) {
        return new Reservation(
                ReservationId.of(id),
                HOST_A,
                ReservationKind.TASK,
                new ReservedResources(
                        cpu,
                        memory,
                        storage,
                        Map.of(DEVICE_ID, new DeviceResourceReservation(
                                DEVICE_ID, 0, deviceCompute, 0, 0))),
                state);
    }

    private static PhysicalHostAvailability availability(
            PhysicalHostId hostId,
            PhysicalHostIncarnationId incarnationId,
            AvailabilityState state) {
        return new PhysicalHostAvailability(hostId, incarnationId, state);
    }

    private static LocalWorkerRuntimeIncarnationBinding binding(
            WorkerRuntimeId runtimeId,
            WorkerRuntimeIncarnationId incarnationId) {
        return new LocalWorkerRuntimeIncarnationBinding(
                runtimeId, incarnationId, HOST_A, HOST_A_BOOT_1);
    }

    private static WorkerRuntimeDescriptor descriptor(WorkerRuntimeId runtimeId) {
        return WorkerRuntimeDescriptor.local(
                runtimeId, RuntimeLifecycleKind.RESIDENT_RUNTIME, HOST_A);
    }

    private static void assertUnavailable(SchedulableCapacity result) {
        assertThat(result.available()).isFalse();
        assertThat(result.cpu().millicores()).isZero();
        assertThat(result.memory().bytes()).isZero();
        assertThat(result.temporaryStorage().bytes()).isZero();
        assertThat(result.deviceResources().get(DEVICE_ID))
                .isEqualTo(DeviceResourceCapacity.none(DEVICE_ID));
    }
}
