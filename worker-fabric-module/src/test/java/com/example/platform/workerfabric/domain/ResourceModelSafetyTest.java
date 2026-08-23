package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceModelSafetyTest {

    private static final PhysicalHostId HOST_ID = PhysicalHostId.of("host-1");
    private static final PhysicalHostIncarnationId HOST_INCARNATION =
            PhysicalHostIncarnationId.of("host-boot-1");
    private static final DeviceId DEVICE_ID = DeviceId.of("accelerator-0");
    private static final WorkerRuntimeId RUNTIME_ID = WorkerRuntimeId.of("runtime-1");
    private static final WorkerRuntimeIncarnationId RUNTIME_INCARNATION =
            WorkerRuntimeIncarnationId.of("runtime-registration-1");

    @Test
    void activeReservationReducesSchedulableCapacity() {
        Reservation active = reservation(
                "active",
                ReservationKind.TASK,
                ReservationState.ACTIVE,
                resources(1_000, 8_000, 10_000, 2_000, 25, 1, 0));
        SafetyHeadroom headroom = new SafetyHeadroom(
                resources(500, 4_000, 5_000, 1_000, 5, 0, 1));

        SchedulableCapacity result = SchedulableCapacity.forHost(
                staticCapacity(), List.of(active), headroom, reachableHost());

        assertThat(result.available()).isTrue();
        assertThat(result.cpu().millicores()).isEqualTo(6_500);
        assertThat(result.memory().bytes()).isEqualTo(52_000);
        assertThat(result.temporaryStorage().bytes()).isEqualTo(85_000);
        assertThat(result.deviceResources().get(DEVICE_ID))
                .isEqualTo(new DeviceResourceCapacity(DEVICE_ID, 13_000, 70, 1, 1));
    }

    @Test
    void recoveryHoldReservationReducesSchedulableCapacity() {
        Reservation recoveryHold = reservation(
                "recovery-hold",
                ReservationKind.TASK,
                ReservationState.RECOVERY_HOLD,
                resources(2_000, 16_000, 20_000, 4_000, 50, 1, 1));

        SchedulableCapacity result = SchedulableCapacity.forHost(
                staticCapacity(), List.of(recoveryHold), SafetyHeadroom.none(), reachableHost());

        assertThat(recoveryHold.keepsCapacityUnavailable()).isTrue();
        assertThat(result.cpu().millicores()).isEqualTo(6_000);
        assertThat(result.deviceResources().get(DEVICE_ID).computeUnits()).isEqualTo(50);
    }

    @Test
    void releasedReservationDoesNotReduceSchedulableCapacity() {
        Reservation released = reservation(
                "released",
                ReservationKind.TASK,
                ReservationState.RELEASED,
                resources(8_000, 64_000, 100_000, 16_000, 100, 2, 2));

        SchedulableCapacity result = SchedulableCapacity.forHost(
                staticCapacity(), List.of(released), SafetyHeadroom.none(), reachableHost());

        assertThat(released.keepsCapacityUnavailable()).isFalse();
        assertThat(result).isEqualTo(fullSchedulableCapacity());
    }

    @Test
    void residentReservationReducesSchedulableCapacity() {
        Reservation resident = reservation(
                "resident",
                ReservationKind.RESIDENT_RUNTIME,
                ReservationState.ACTIVE,
                resources(1_500, 24_000, 0, 8_000, 60, 1, 1));

        SchedulableCapacity result = SchedulableCapacity.forHost(
                staticCapacity(), List.of(resident), SafetyHeadroom.none(), reachableHost());

        assertThat(resident.isResident()).isTrue();
        assertThat(result.cpu().millicores()).isEqualTo(6_500);
        assertThat(result.memory().bytes()).isEqualTo(40_000);
        assertThat(result.deviceResources().get(DEVICE_ID).vramBytes()).isEqualTo(8_000);
    }

    @Test
    void observedLowUtilizationDoesNotReleaseReservationOrMintCapacity() {
        Reservation resident = reservation(
                "resident-low-use",
                ReservationKind.RESIDENT_RUNTIME,
                ReservationState.ACTIVE,
                resources(2_000, 32_000, 0, 12_000, 75, 2, 2));
        ObservedUsage lowUsage = new ObservedUsage(
                new ObservedCpuUsage(0.01),
                new ObservedMemoryUsage(1),
                new ObservedTemporaryStorageUsage(1),
                Map.of(DEVICE_ID, new ObservedDeviceUsage(DEVICE_ID, 0.01, 1, 0.0, 0.0)));

        SchedulableCapacity result = SchedulableCapacity.forHost(
                staticCapacity(), List.of(resident), SafetyHeadroom.none(), reachableHost());

        assertThat(lowUsage.cpu().utilizationRatio()).isEqualTo(0.01);
        assertThat(resident.state()).isEqualTo(ReservationState.ACTIVE);
        assertThat(result.cpu().millicores()).isEqualTo(6_000);
        assertThat(result.deviceResources().get(DEVICE_ID).computeUnits()).isEqualTo(25);
        assertThat(Arrays.stream(SchedulableCapacity.class.getDeclaredMethods())
                        .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .doesNotContain(ObservedUsage.class);
    }

    @Test
    void unreachableHostOrRuntimeExposesNoSchedulableCapacityWithoutDeletingIdentity() {
        PhysicalHostAvailability unreachableHost = new PhysicalHostAvailability(
                HOST_ID, HOST_INCARNATION, AvailabilityState.UNREACHABLE);
        WorkerRuntimeAvailability unreachableRuntime = new WorkerRuntimeAvailability(
                WorkerRuntimeId.of("runtime-1"),
                WorkerRuntimeIncarnationId.of("runtime-registration-1"),
                AvailabilityState.UNREACHABLE);

        SchedulableCapacity hostResult = SchedulableCapacity.forHost(
                staticCapacity(), List.of(), SafetyHeadroom.none(), unreachableHost);
        SchedulableCapacity runtimeResult = SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                unreachableRuntime,
                currentBinding(),
                localRuntimeDescriptor());

        assertThat(hostResult.available()).isFalse();
        assertThat(hostResult.cpu().millicores()).isZero();
        assertThat(hostResult.memory().bytes()).isZero();
        assertThat(hostResult.temporaryStorage().bytes()).isZero();
        assertThat(hostResult.deviceResources().get(DEVICE_ID))
                .isEqualTo(DeviceResourceCapacity.none(DEVICE_ID));
        assertThat(runtimeResult.available()).isFalse();
        assertThat(unreachableHost.physicalHostId()).isEqualTo(HOST_ID);
    }

    @Test
    void stableHostIdAndNewHostIncarnationRemainDistinct() {
        PhysicalHostAvailability beforeRestart = reachableHost();
        PhysicalHostAvailability afterRestart = new PhysicalHostAvailability(
                HOST_ID,
                PhysicalHostIncarnationId.of("host-boot-2"),
                AvailabilityState.REACHABLE);

        assertThat(afterRestart.physicalHostId()).isEqualTo(beforeRestart.physicalHostId());
        assertThat(afterRestart.incarnationId()).isNotEqualTo(beforeRestart.incarnationId());
    }

    @Test
    void stableWorkerRuntimeIdAndNewRuntimeIncarnationRemainDistinct() {
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-1");
        WorkerRuntimeAvailability beforeRestart = new WorkerRuntimeAvailability(
                runtimeId,
                WorkerRuntimeIncarnationId.of("runtime-registration-1"),
                AvailabilityState.REACHABLE);
        WorkerRuntimeAvailability afterRestart = new WorkerRuntimeAvailability(
                runtimeId,
                WorkerRuntimeIncarnationId.of("runtime-registration-2"),
                AvailabilityState.REACHABLE);

        assertThat(afterRestart.workerRuntimeId()).isEqualTo(beforeRestart.workerRuntimeId());
        assertThat(afterRestart.incarnationId()).isNotEqualTo(beforeRestart.incarnationId());
    }

    @Test
    void staleIncarnationEqualityDoesNotPassAsCurrent() {
        PhysicalHostAvailability currentHost = reachableHost();
        WorkerRuntimeAvailability currentRuntime = new WorkerRuntimeAvailability(
                WorkerRuntimeId.of("runtime-1"),
                WorkerRuntimeIncarnationId.of("runtime-registration-2"),
                AvailabilityState.REACHABLE);

        assertThat(currentHost.matchesCurrentIncarnation(
                        HOST_ID, PhysicalHostIncarnationId.of("host-boot-0")))
                .isFalse();
        assertThat(currentRuntime.matchesCurrentIncarnation(
                        WorkerRuntimeId.of("runtime-1"),
                        WorkerRuntimeIncarnationId.of("runtime-registration-1")))
                .isFalse();
    }

    private static PhysicalHostAvailability reachableHost() {
        return new PhysicalHostAvailability(HOST_ID, HOST_INCARNATION, AvailabilityState.REACHABLE);
    }

    private static LocalWorkerRuntimeIncarnationBinding currentBinding() {
        return new LocalWorkerRuntimeIncarnationBinding(
                RUNTIME_ID, RUNTIME_INCARNATION, HOST_ID, HOST_INCARNATION);
    }

    private static WorkerRuntimeDescriptor localRuntimeDescriptor() {
        return WorkerRuntimeDescriptor.local(
                RUNTIME_ID, RuntimeLifecycleKind.RESIDENT_RUNTIME, HOST_ID);
    }

    private static CapacitySnapshot staticCapacity() {
        return new CapacitySnapshot(
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000),
                TemporaryStorageCapacity.ofBytes(100_000),
                Map.of(DEVICE_ID, new DeviceResourceCapacity(DEVICE_ID, 16_000, 100, 2, 2)));
    }

    private static SchedulableCapacity fullSchedulableCapacity() {
        return new SchedulableCapacity(
                true,
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000),
                TemporaryStorageCapacity.ofBytes(100_000),
                Map.of(DEVICE_ID, new DeviceResourceCapacity(DEVICE_ID, 16_000, 100, 2, 2)));
    }

    private static Reservation reservation(
            String id,
            ReservationKind kind,
            ReservationState state,
            ReservedResources resources) {
        return new Reservation(ReservationId.of(id), HOST_ID, kind, resources, state);
    }

    private static ReservedResources resources(
            long cpuMillicores,
            long memoryBytes,
            long temporaryStorageBytes,
            long vramBytes,
            long computeUnits,
            long encoderEngines,
            long decoderEngines) {
        return new ReservedResources(
                cpuMillicores,
                memoryBytes,
                temporaryStorageBytes,
                Map.of(DEVICE_ID, new DeviceResourceReservation(
                        DEVICE_ID,
                        vramBytes,
                        computeUnits,
                        encoderEngines,
                        decoderEngines)));
    }
}
