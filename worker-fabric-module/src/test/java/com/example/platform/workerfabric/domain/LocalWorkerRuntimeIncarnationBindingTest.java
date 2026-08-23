package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalWorkerRuntimeIncarnationBindingTest {

    private static final PhysicalHostId HOST_ID = PhysicalHostId.of("host-1");
    private static final PhysicalHostIncarnationId HOST_INCARNATION =
            PhysicalHostIncarnationId.of("host-boot-1");
    private static final WorkerRuntimeId RUNTIME_ID = WorkerRuntimeId.of("runtime-1");
    private static final WorkerRuntimeIncarnationId RUNTIME_INCARNATION =
            WorkerRuntimeIncarnationId.of("runtime-registration-1");

    @Test
    void matchingRuntimeHostIncarnationBindingSucceeds() {
        SchedulableCapacity result = SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                reachableRuntime(),
                currentBinding(),
                localDescriptor(HOST_ID));

        assertThat(result.available()).isTrue();
        assertThat(result.cpu()).isEqualTo(CpuCapacity.ofMillicores(8_000));
    }

    @Test
    void wrongPhysicalHostIdFailsClosed() {
        LocalWorkerRuntimeIncarnationBinding wrongHost = new LocalWorkerRuntimeIncarnationBinding(
                RUNTIME_ID,
                RUNTIME_INCARNATION,
                PhysicalHostId.of("host-2"),
                HOST_INCARNATION);

        assertBindingRejected(wrongHost, reachableHost(), reachableRuntime());
    }

    @Test
    void stalePhysicalHostIncarnationIdFailsClosed() {
        LocalWorkerRuntimeIncarnationBinding staleHost = new LocalWorkerRuntimeIncarnationBinding(
                RUNTIME_ID,
                RUNTIME_INCARNATION,
                HOST_ID,
                PhysicalHostIncarnationId.of("host-boot-0"));

        assertBindingRejected(staleHost, reachableHost(), reachableRuntime());
    }

    @Test
    void wrongWorkerRuntimeIdFailsClosed() {
        LocalWorkerRuntimeIncarnationBinding wrongRuntime = new LocalWorkerRuntimeIncarnationBinding(
                WorkerRuntimeId.of("runtime-2"),
                RUNTIME_INCARNATION,
                HOST_ID,
                HOST_INCARNATION);

        assertBindingRejected(wrongRuntime, reachableHost(), reachableRuntime());
    }

    @Test
    void staleWorkerRuntimeIncarnationIdFailsClosed() {
        LocalWorkerRuntimeIncarnationBinding staleRuntime = new LocalWorkerRuntimeIncarnationBinding(
                RUNTIME_ID,
                WorkerRuntimeIncarnationId.of("runtime-registration-0"),
                HOST_ID,
                HOST_INCARNATION);

        assertBindingRejected(staleRuntime, reachableHost(), reachableRuntime());
    }

    @Test
    void descriptorHostMismatchFailsClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                reachableRuntime(),
                currentBinding(),
                localDescriptor(PhysicalHostId.of("host-2"))));
    }

    @Test
    void unreachableHostRemainsUnavailable() {
        PhysicalHostAvailability unreachableHost = new PhysicalHostAvailability(
                HOST_ID, HOST_INCARNATION, AvailabilityState.UNREACHABLE);

        SchedulableCapacity result = SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                unreachableHost,
                reachableRuntime(),
                currentBinding(),
                localDescriptor(HOST_ID));

        assertUnavailable(result);
    }

    @Test
    void unreachableRuntimeRemainsUnavailable() {
        WorkerRuntimeAvailability unreachableRuntime = new WorkerRuntimeAvailability(
                RUNTIME_ID, RUNTIME_INCARNATION, AvailabilityState.UNREACHABLE);

        SchedulableCapacity result = SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                unreachableRuntime,
                currentBinding(),
                localDescriptor(HOST_ID));

        assertUnavailable(result);
    }

    @Test
    void descriptorRuntimeMismatchFailsClosed() {
        WorkerRuntimeDescriptor wrongRuntimeDescriptor = WorkerRuntimeDescriptor.local(
                WorkerRuntimeId.of("runtime-2"), RuntimeLifecycleKind.RESIDENT_RUNTIME, HOST_ID);

        assertThatIllegalArgumentException().isThrownBy(() -> SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                reachableRuntime(),
                currentBinding(),
                wrongRuntimeDescriptor));
    }

    @Test
    void remoteRuntimeDescriptorWithFabricatedHostBindingFailsClosed() {
        WorkerRuntimeDescriptor remoteDescriptor = WorkerRuntimeDescriptor.remote(RUNTIME_ID);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SchedulableCapacity.forLocalRuntime(
                        staticCapacity(),
                        List.of(),
                        SafetyHeadroom.none(),
                        reachableHost(),
                        reachableRuntime(),
                        currentBinding(),
                        remoteDescriptor))
                .withMessageContaining("REMOTE_RUNTIME");
    }

    @Test
    void localRuntimeCapacityCannotBeCalledWithoutDescriptor() {
        assertThat(localRuntimeCapacityEntrypoints())
                .hasSize(1)
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .contains(WorkerRuntimeDescriptor.class));
    }

    @Test
    void localRuntimeCapacityCannotBeCalledWithoutIncarnationBinding() {
        assertThat(localRuntimeCapacityEntrypoints())
                .hasSize(1)
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .contains(LocalWorkerRuntimeIncarnationBinding.class));
    }

    @Test
    void matchingLocalDescriptorBindingAndAvailabilitySucceeds() {
        SchedulableCapacity result = SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                reachableRuntime(),
                currentBinding(),
                localDescriptor(HOST_ID));

        assertThat(result.available()).isTrue();
        assertThat(result.cpu()).isEqualTo(CpuCapacity.ofMillicores(8_000));
    }

    @Test
    void descriptorStableHostMismatchFailsClosed() {
        WorkerRuntimeDescriptor wrongHostDescriptor =
                localDescriptor(PhysicalHostId.of("host-2"));

        assertThatIllegalArgumentException().isThrownBy(() -> SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                reachableHost(),
                reachableRuntime(),
                currentBinding(),
                wrongHostDescriptor));
    }

    @Test
    void runtimeCapacityApiHasOneBindingAuthorityAndNoBindinglessEntryPoint() {
        List<Method> runtimeCapacityMethods = localRuntimeCapacityEntrypoints();

        assertThat(runtimeCapacityMethods)
                .singleElement()
                .extracting(Method::getName)
                .isEqualTo("forLocalRuntime");
        assertThat(runtimeCapacityMethods)
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .contains(
                                LocalWorkerRuntimeIncarnationBinding.class,
                                WorkerRuntimeDescriptor.class));
        assertThat(Arrays.stream(LocalWorkerRuntimeIncarnationBinding.class.getRecordComponents())
                        .map(component -> component.getType().getSimpleName()))
                .containsExactly(
                        "WorkerRuntimeId",
                        "WorkerRuntimeIncarnationId",
                        "PhysicalHostId",
                        "PhysicalHostIncarnationId");
    }

    private static void assertBindingRejected(
            LocalWorkerRuntimeIncarnationBinding binding,
            PhysicalHostAvailability hostAvailability,
            WorkerRuntimeAvailability runtimeAvailability) {
        assertThatIllegalArgumentException().isThrownBy(() -> SchedulableCapacity.forLocalRuntime(
                staticCapacity(),
                List.of(),
                SafetyHeadroom.none(),
                hostAvailability,
                runtimeAvailability,
                binding,
                localDescriptor(HOST_ID)));
    }

    private static List<Method> localRuntimeCapacityEntrypoints() {
        return Arrays.stream(SchedulableCapacity.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType().equals(SchedulableCapacity.class))
                .filter(method -> !method.getName().equals("forHost"))
                .toList();
    }

    private static void assertUnavailable(SchedulableCapacity result) {
        assertThat(result.available()).isFalse();
        assertThat(result.cpu().millicores()).isZero();
        assertThat(result.memory().bytes()).isZero();
        assertThat(result.temporaryStorage().bytes()).isZero();
    }

    private static PhysicalHostAvailability reachableHost() {
        return new PhysicalHostAvailability(HOST_ID, HOST_INCARNATION, AvailabilityState.REACHABLE);
    }

    private static WorkerRuntimeAvailability reachableRuntime() {
        return new WorkerRuntimeAvailability(
                RUNTIME_ID, RUNTIME_INCARNATION, AvailabilityState.REACHABLE);
    }

    private static LocalWorkerRuntimeIncarnationBinding currentBinding() {
        return new LocalWorkerRuntimeIncarnationBinding(
                RUNTIME_ID, RUNTIME_INCARNATION, HOST_ID, HOST_INCARNATION);
    }

    private static WorkerRuntimeDescriptor localDescriptor(PhysicalHostId hostId) {
        return WorkerRuntimeDescriptor.local(
                RUNTIME_ID, RuntimeLifecycleKind.RESIDENT_RUNTIME, hostId);
    }

    private static CapacitySnapshot staticCapacity() {
        return new CapacitySnapshot(
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000),
                TemporaryStorageCapacity.ofBytes(100_000),
                Map.of());
    }
}
