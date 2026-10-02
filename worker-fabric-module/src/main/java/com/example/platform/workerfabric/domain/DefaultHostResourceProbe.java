package com.example.platform.workerfabric.domain;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded V1 default {@link HostResourceAgent.ResourceProbe} for the local single host.
 *
 * <p>It reads exactly the kernel evidence the bounded configuration needs — CPU capacity from
 * {@code /proc/cpuinfo}, memory capacity and availability from {@code /proc/meminfo}, host CPU
 * utilisation from the first aggregate line of {@code /proc/stat}, and temporary-storage capacity
 * from the configured work-root file store.
 *
 * <p><b>Fail closed.</b> Any missing or unreadable input raises
 * {@link HostObservationUnavailableException}. The probe never substitutes a default, floor or
 * synthetic capacity/usage value — corrupt authoritative capacity would be accepted by the grant
 * chain.
 *
 * <p><b>Bounded device inventory.</b> The ffmpeg sandbox needs CPU, memory and temporary storage
 * only, so the probe declares an empty device inventory and empty device maps (no GPU inventory in
 * bounded V1). Fingerprinting is deterministic: it derives only from the configured host identity
 * and location, never from wall-clock or random state.
 */
public final class DefaultHostResourceProbe implements HostResourceAgent.ResourceProbe {

    private static final long KIBIBYTE = 1024L;
    private static final Path DEFAULT_PROC_ROOT = Path.of("/proc");

    private final Path procRoot;
    private final Path temporaryStorage;
    private final HostLocation location;
    private final TrustZoneId trustZone;

    public DefaultHostResourceProbe(
            Path procRoot, Path temporaryStorage, HostLocation location, TrustZoneId trustZone) {
        this.procRoot = Objects.requireNonNull(procRoot, "procRoot");
        this.temporaryStorage = Objects.requireNonNull(temporaryStorage, "temporaryStorage");
        this.location = Objects.requireNonNull(location, "location");
        this.trustZone = Objects.requireNonNull(trustZone, "trustZone");
    }

    /** Bounded V1 composition: the real {@code /proc} and the worker's temporary-storage root. */
    public static DefaultHostResourceProbe forLocalHost(Path temporaryStorage) {
        return new DefaultHostResourceProbe(
                DEFAULT_PROC_ROOT, temporaryStorage, HostLocation.of("local"), TrustZoneId.of("local"));
    }

    /**
     * The temporary-storage root whose file store supplies this probe's temporary-storage capacity and
     * observed usage. Behaviour-neutral accessor: it exposes the configured path only, never new
     * measurement behaviour.
     */
    public Path workRoot() {
        return temporaryStorage;
    }

    @Override
    public PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId physicalHostId) {
        // Deterministic: identity + configured location only; bounded V1 carries no device inventory.
        return new PhysicalHostDescriptor(
                Objects.requireNonNull(physicalHostId, "physicalHostId"),
                location, trustZone, List.of());
    }

    @Override
    public CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor hostDescriptor) {
        Objects.requireNonNull(hostDescriptor, "hostDescriptor");
        return new CapacitySnapshot(
                CpuCapacity.ofMillicores(cpuMillicores()),
                MemoryCapacity.ofBytes(memInfoKib("MemTotal") * KIBIBYTE),
                TemporaryStorageCapacity.ofBytes(temporaryStorageTotalBytes()),
                Map.of());
    }

    @Override
    public ObservedUsage collectHostAndDeviceObservation(PhysicalHostDescriptor hostDescriptor) {
        Objects.requireNonNull(hostDescriptor, "hostDescriptor");
        long totalMemory = memInfoKib("MemTotal") * KIBIBYTE;
        long availableMemory = memInfoKib("MemAvailable") * KIBIBYTE;
        return new ObservedUsage(
                new ObservedCpuUsage(aggregateCpuUtilizationRatio()),
                new ObservedMemoryUsage(Math.max(0L, totalMemory - availableMemory)),
                new ObservedTemporaryStorageUsage(temporaryStorageUsedBytes()),
                Map.of());
    }

    // ---------- host evidence readers (each fails closed) ----------

    private long cpuMillicores() {
        long processors = read(procRoot.resolve("cpuinfo")).lines()
                .filter(line -> line.startsWith("processor"))
                .count();
        if (processors <= 0) {
            throw new HostObservationUnavailableException(
                    "no processor entries in " + procRoot.resolve("cpuinfo"));
        }
        return processors * 1000L;
    }

    private long memInfoKib(String key) {
        String prefix = key + ":";
        return read(procRoot.resolve("meminfo")).lines()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .map(value -> value.replace("kB", "").trim())
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException malformed) {
                        throw new HostObservationUnavailableException(
                                "malformed " + key + " in /proc/meminfo: " + value, malformed);
                    }
                })
                .orElseThrow(() -> new HostObservationUnavailableException(
                        "absent " + key + " in " + procRoot.resolve("meminfo")));
    }

    /** Aggregate host utilisation over one sample: {@code 1 - (idle + iowait) / total}. */
    private double aggregateCpuUtilizationRatio() {
        String aggregate = read(procRoot.resolve("stat")).lines()
                .filter(line -> line.startsWith("cpu "))
                .findFirst()
                .orElseThrow(() -> new HostObservationUnavailableException(
                        "absent aggregate cpu line in " + procRoot.resolve("stat")));
        String[] fields = aggregate.trim().split("\\s+");
        if (fields.length < 6) {
            throw new HostObservationUnavailableException(
                    "malformed aggregate cpu line in /proc/stat: " + aggregate);
        }
        long total = 0L;
        long idle = 0L;
        try {
            for (int index = 1; index < fields.length; index++) {
                long value = Long.parseLong(fields[index]);
                total += value;
                if (index == 4 || index == 5) {
                    idle += value;
                }
            }
        } catch (NumberFormatException malformed) {
            throw new HostObservationUnavailableException(
                    "malformed aggregate cpu counters in /proc/stat", malformed);
        }
        if (total <= 0L) {
            throw new HostObservationUnavailableException(
                    "non-positive aggregate cpu counter total in /proc/stat");
        }
        double utilization = 1.0 - ((double) idle / (double) total);
        return Math.max(0.0, Math.min(1.0, utilization));
    }

    private long temporaryStorageTotalBytes() {
        return fileStore().map(store -> {
            try {
                return store.getTotalSpace();
            } catch (IOException failure) {
                throw new HostObservationUnavailableException(
                        "temporary-storage total space unavailable for " + temporaryStorage, failure);
            }
        }).orElseThrow();
    }

    private long temporaryStorageUsedBytes() {
        return fileStore().map(store -> {
            try {
                return Math.max(0L, store.getTotalSpace() - store.getUsableSpace());
            } catch (IOException failure) {
                throw new HostObservationUnavailableException(
                        "temporary-storage usable space unavailable for " + temporaryStorage, failure);
            }
        }).orElseThrow();
    }

    private java.util.Optional<FileStore> fileStore() {
        try {
            if (!Files.isDirectory(temporaryStorage)) {
                throw new HostObservationUnavailableException(
                        "temporary-storage root is not a directory: " + temporaryStorage);
            }
            return java.util.Optional.of(Files.getFileStore(temporaryStorage));
        } catch (IOException failure) {
            throw new HostObservationUnavailableException(
                    "temporary-storage file store unavailable for " + temporaryStorage, failure);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException | RuntimeException failure) {
            throw new HostObservationUnavailableException(
                    "host evidence unreadable: " + path, failure);
        }
    }
}
