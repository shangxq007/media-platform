package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2-5b-2a-1-1: the bounded default host probe parses real kernel evidence shapes and fails closed
 * when that evidence is unavailable — it never invents capacity or usage.
 */
class DefaultHostResourceProbeTest {

    private static final PhysicalHostId HOST_ID = PhysicalHostId.of("local-host");
    private static final HostLocation LOCATION = HostLocation.of("local");
    private static final TrustZoneId TRUST_ZONE = TrustZoneId.of("local");

    @TempDir
    Path temporaryStorage;

    @Test
    void collectsCpuMemoryAndTemporaryStorageEvidence(@TempDir Path procRoot) throws IOException {
        writeProc(procRoot, 4, "MemTotal:       1048576 kB\nMemAvailable:    524288 kB\n",
                "cpu  100 0 100 800 0 0 0 0 0 0\ncpu0 50 0 50 400 0 0 0 0 0 0\n");
        DefaultHostResourceProbe probe = probe(procRoot);

        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(HOST_ID);
        assertThat(descriptor.id()).isEqualTo(HOST_ID);
        assertThat(descriptor.location()).isEqualTo(LOCATION);
        assertThat(descriptor.trustZoneId()).isEqualTo(TRUST_ZONE);
        // Bounded V1: no GPU/media device inventory.
        assertThat(descriptor.devices()).isEmpty();

        CapacitySnapshot capacity = probe.collectStaticCapacity(descriptor);
        assertThat(capacity.cpu().millicores()).isEqualTo(4000L);
        assertThat(capacity.memory().bytes()).isEqualTo(1073741824L);
        assertThat(capacity.temporaryStorage().bytes()).isGreaterThan(0L);
        assertThat(capacity.deviceResources()).isEmpty();

        ObservedUsage usage = probe.collectHostAndDeviceObservation(descriptor);
        // total = 1000, idle+iowait = 800 -> utilisation 0.2
        assertThat(usage.cpu().utilizationRatio()).isCloseTo(0.2d, within(1e-9));
        assertThat(usage.memory().usedBytes()).isEqualTo(536870912L);
        assertThat(usage.temporaryStorage().usedBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(usage.deviceUsage()).isEmpty();
    }

    @Test
    void isDeterministicForIdenticalEvidence(@TempDir Path procRoot) throws IOException {
        writeProc(procRoot, 2, "MemTotal:        524288 kB\nMemAvailable:    262144 kB\n",
                "cpu  10 0 10 80 0 0 0 0 0 0\n");
        DefaultHostResourceProbe probe = probe(procRoot);

        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(HOST_ID);
        assertThat(probe.fingerprintStaticHostResources(HOST_ID)).isEqualTo(descriptor);
        assertThat(probe.collectStaticCapacity(descriptor))
                .isEqualTo(probe.collectStaticCapacity(descriptor));
        assertThat(probe.collectHostAndDeviceObservation(descriptor))
                .isEqualTo(probe.collectHostAndDeviceObservation(descriptor));
    }

    @Test
    void failsClosedWhenProcEvidenceIsUnavailable(@TempDir Path emptyProcRoot) {
        DefaultHostResourceProbe probe = probe(emptyProcRoot);
        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(HOST_ID);

        assertThatThrownBy(() -> probe.collectStaticCapacity(descriptor))
                .isInstanceOf(HostObservationUnavailableException.class)
                .hasMessageContaining("unreadable");
        assertThatThrownBy(() -> probe.collectHostAndDeviceObservation(descriptor))
                .isInstanceOf(HostObservationUnavailableException.class)
                .hasMessageContaining("unreadable");
    }

    @Test
    void failsClosedOnMalformedCpuCounters(@TempDir Path procRoot) throws IOException {
        writeProc(procRoot, 1, "MemTotal:        1024 kB\nMemAvailable:     512 kB\n",
                "cpu  not-a-number 0 0 0 0 0 0 0 0 0\n");
        DefaultHostResourceProbe probe = probe(procRoot);
        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(HOST_ID);

        assertThatThrownBy(() -> probe.collectHostAndDeviceObservation(descriptor))
                .isInstanceOf(HostObservationUnavailableException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void failsClosedWhenTemporaryStorageRootIsAbsent(@TempDir Path procRoot) throws IOException {
        writeProc(procRoot, 2, "MemTotal:        524288 kB\nMemAvailable:    262144 kB\n",
                "cpu  10 0 10 80 0 0 0 0 0 0\n");
        DefaultHostResourceProbe probe = new DefaultHostResourceProbe(
                procRoot, temporaryStorage.resolve("absent"), LOCATION, TRUST_ZONE);
        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(HOST_ID);

        assertThatThrownBy(() -> probe.collectStaticCapacity(descriptor))
                .isInstanceOf(HostObservationUnavailableException.class)
                .hasMessageContaining("not a directory");
    }

    private DefaultHostResourceProbe probe(Path procRoot) {
        return new DefaultHostResourceProbe(procRoot, temporaryStorage, LOCATION, TRUST_ZONE);
    }

    private static void writeProc(Path procRoot, int processors, String meminfo, String stat)
            throws IOException {
        StringBuilder cpuinfo = new StringBuilder();
        for (int index = 0; index < processors; index++) {
            cpuinfo.append("processor\t: ").append(index).append('\n');
        }
        Files.writeString(procRoot.resolve("cpuinfo"), cpuinfo.toString());
        Files.writeString(procRoot.resolve("meminfo"), meminfo);
        Files.writeString(procRoot.resolve("stat"), stat);
    }
}
