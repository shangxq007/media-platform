package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkerFabricDescriptorTest {

    @Test
    void lifecycleKindsAreExactlyThePhaseTwoFoundation() {
        assertThat(RuntimeLifecycleKind.values()).containsExactly(
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                RuntimeLifecycleKind.RESIDENT_RUNTIME,
                RuntimeLifecycleKind.REMOTE_RUNTIME);
    }

    @Test
    void deviceKindsAreProviderNeutralAndBounded() {
        assertThat(DeviceKind.values()).containsExactly(
                DeviceKind.CPU,
                DeviceKind.GPU,
                DeviceKind.MEDIA_ACCELERATOR,
                DeviceKind.OTHER_ACCELERATOR);
    }

    @Test
    void localRuntimeIdentifiesItsPhysicalHost() {
        PhysicalHostId hostId = PhysicalHostId.of("host-1");
        WorkerRuntimeDescriptor descriptor = WorkerRuntimeDescriptor.local(
                WorkerRuntimeId.of("runtime-1"),
                RuntimeLifecycleKind.RESIDENT_RUNTIME,
                hostId);

        assertThat(descriptor.physicalHostId()).contains(hostId);
    }

    @Test
    void onePhysicalHostMayHostMultipleWorkerRuntimes() {
        PhysicalHostId hostId = PhysicalHostId.of("host-1");
        WorkerRuntimeDescriptor ephemeral = WorkerRuntimeDescriptor.local(
                WorkerRuntimeId.of("runtime-ephemeral"),
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                hostId);
        WorkerRuntimeDescriptor resident = WorkerRuntimeDescriptor.local(
                WorkerRuntimeId.of("runtime-resident"),
                RuntimeLifecycleKind.RESIDENT_RUNTIME,
                hostId);

        assertThat(ephemeral.id()).isNotEqualTo(resident.id());
        assertThat(ephemeral.physicalHostId()).contains(hostId);
        assertThat(resident.physicalHostId()).contains(hostId);
    }

    @Test
    void restartCreatesNewIncarnationWithoutChangingStableIdentity() {
        PhysicalHostId stableHostId = PhysicalHostId.of("host-1");
        WorkerRuntimeId stableRuntimeId = WorkerRuntimeId.of("runtime-1");

        assertThat(PhysicalHostId.of("host-1")).isEqualTo(stableHostId);
        assertThat(PhysicalHostIncarnationId.of("host-boot-1"))
                .isNotEqualTo(PhysicalHostIncarnationId.of("host-boot-2"));
        assertThat(WorkerRuntimeId.of("runtime-1")).isEqualTo(stableRuntimeId);
        assertThat(WorkerRuntimeIncarnationId.of("runtime-registration-1"))
                .isNotEqualTo(WorkerRuntimeIncarnationId.of("runtime-registration-2"));
    }

    @Test
    void remoteRuntimeHasNoFabricatedPhysicalHost() {
        WorkerRuntimeDescriptor descriptor =
                WorkerRuntimeDescriptor.remote(WorkerRuntimeId.of("runtime-remote"));

        assertThat(descriptor.lifecycleKind()).isEqualTo(RuntimeLifecycleKind.REMOTE_RUNTIME);
        assertThat(descriptor.physicalHostId()).isEmpty();
    }

    @Test
    void invalidHostLifecycleCombinationsFailClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkerRuntimeDescriptor(
                WorkerRuntimeId.of("runtime-local"),
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                Optional.empty()));

        assertThatIllegalArgumentException().isThrownBy(() -> new WorkerRuntimeDescriptor(
                WorkerRuntimeId.of("runtime-remote"),
                RuntimeLifecycleKind.REMOTE_RUNTIME,
                Optional.of(PhysicalHostId.of("host-1"))));
    }

    @Test
    void physicalHostDeviceInventoryIsImmutable() {
        List<DeviceDescriptor> devices = new ArrayList<>();
        devices.add(new DeviceDescriptor(
                DeviceId.of("cpu-0"),
                DeviceKind.CPU,
                DeviceVendor.of("vendor-a"),
                DeviceModel.of("model-a")));

        PhysicalHostDescriptor descriptor = new PhysicalHostDescriptor(
                PhysicalHostId.of("host-1"),
                HostLocation.of("region-a/zone-1"),
                TrustZoneId.of("trusted-internal"),
                devices);
        devices.clear();

        assertThat(descriptor.devices()).hasSize(1);
        assertThatThrownBy(() -> descriptor.devices().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
