package com.example.platform.workerfabric.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded producer of host-level resource evidence.
 *
 * <p>It probes and publishes evidence only. Scheduling, task selection, provider legality,
 * reservations, and execution-attempt state remain outside this component.
 */
public final class HostResourceAgent {

    /** Host-local mechanics for static fingerprinting and host/device observation. */
    public interface ResourceProbe {

        PhysicalHostDescriptor fingerprintStaticHostResources(PhysicalHostId physicalHostId);

        CapacitySnapshot collectStaticCapacity(PhysicalHostDescriptor hostDescriptor);

        ObservedUsage collectHostAndDeviceObservation(PhysicalHostDescriptor hostDescriptor);
    }

    /** Evidence update port; the recipient does not gain scheduling or reservation authority. */
    @FunctionalInterface
    public interface EvidencePublisher {

        void update(HostResourceSnapshot snapshot);
    }

    /** Narrow read port into the durable per-host-incarnation generation authority. */
    @FunctionalInterface
    public interface DurableGenerationLookup {

        Optional<HostResourceSnapshotGeneration> currentGeneration(
                PhysicalHostId physicalHostId,
                PhysicalHostIncarnationId physicalHostIncarnationId);
    }

    private final PhysicalHostId physicalHostId;
    private final ResourceProbe resourceProbe;
    private final DurableGenerationLookup durableGenerationLookup;
    private final EvidencePublisher evidencePublisher;
    private final Map<PhysicalHostIncarnationId, HostResourceSnapshotGeneration> generations =
            new HashMap<>();

    public HostResourceAgent(
            PhysicalHostId physicalHostId,
            ResourceProbe resourceProbe,
            DurableGenerationLookup durableGenerationLookup,
            EvidencePublisher evidencePublisher) {
        this.physicalHostId = Objects.requireNonNull(physicalHostId, "physicalHostId");
        this.resourceProbe = Objects.requireNonNull(resourceProbe, "resourceProbe");
        this.durableGenerationLookup =
                Objects.requireNonNull(durableGenerationLookup, "durableGenerationLookup");
        this.evidencePublisher = Objects.requireNonNull(evidencePublisher, "evidencePublisher");
    }

    public synchronized HostResourceSnapshot capture(
            PhysicalHostAvailability currentHost,
            Instant capturedAt,
            Optional<LocalWorkerRuntimeIncarnationBinding> reportingRuntime) {
        Objects.requireNonNull(currentHost, "currentHost");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(reportingRuntime, "reportingRuntime");

        if (!physicalHostId.equals(currentHost.physicalHostId())) {
            throw new IllegalArgumentException(
                    "HostResourceAgent cannot report evidence for another PhysicalHostId");
        }

        PhysicalHostDescriptor descriptor =
                Objects.requireNonNull(
                        resourceProbe.fingerprintStaticHostResources(physicalHostId),
                        "fingerprinted host descriptor");
        if (!physicalHostId.equals(descriptor.id())) {
            throw new IllegalArgumentException(
                    "fingerprinted host descriptor does not match HostResourceAgent identity");
        }

        CapacitySnapshot staticCapacity = Objects.requireNonNull(
                resourceProbe.collectStaticCapacity(descriptor), "static capacity");
        ObservedUsage observedUsage = Objects.requireNonNull(
                resourceProbe.collectHostAndDeviceObservation(descriptor), "observed usage");
        validateDeviceScope(descriptor, staticCapacity, observedUsage);

        Optional<WorkerRuntimeReporterRef> reporter = reportingRuntime.map(binding -> {
            if (!physicalHostId.equals(binding.physicalHostId())
                    || !currentHost.incarnationId().equals(binding.physicalHostIncarnationId())) {
                throw new IllegalArgumentException(
                        "reporting runtime does not bind the current physical-host incarnation");
            }
            return WorkerRuntimeReporterRef.from(binding);
        });

        HostResourceSnapshotGeneration generation = nextGeneration(currentHost.incarnationId());
        HostResourceSnapshot snapshot = new HostResourceSnapshot(
                physicalHostId,
                currentHost.incarnationId(),
                generation,
                capturedAt,
                HostResourceSnapshotSchemaVersion.CURRENT,
                staticCapacity,
                observedUsage,
                reporter);
        evidencePublisher.update(snapshot);
        generations.put(currentHost.incarnationId(), generation);
        return snapshot;
    }

    private HostResourceSnapshotGeneration nextGeneration(
            PhysicalHostIncarnationId physicalHostIncarnationId) {
        HostResourceSnapshotGeneration local = generations.get(physicalHostIncarnationId);
        HostResourceSnapshotGeneration durable = Objects.requireNonNull(
                        durableGenerationLookup.currentGeneration(
                                physicalHostId, physicalHostIncarnationId),
                        "durable generation lookup result")
                .orElse(null);
        HostResourceSnapshotGeneration latest;
        if (local == null) {
            latest = durable;
        } else if (durable == null || local.compareTo(durable) >= 0) {
            latest = local;
        } else {
            latest = durable;
        }
        return latest == null ? HostResourceSnapshotGeneration.first() : latest.next();
    }

    private static void validateDeviceScope(
            PhysicalHostDescriptor descriptor,
            CapacitySnapshot staticCapacity,
            ObservedUsage observedUsage) {
        Set<DeviceId> inventory = new HashSet<>();
        descriptor.devices().forEach(device -> inventory.add(device.id()));
        Set<DeviceId> capacityDevices = staticCapacity.deviceResources().keySet();
        Set<DeviceId> observedDevices = observedUsage.deviceUsage().keySet();
        if (!inventory.containsAll(capacityDevices) || !inventory.containsAll(observedDevices)) {
            throw new IllegalArgumentException(
                    "capacity and observation DeviceIds must belong to the physical-host inventory");
        }
        if (!capacityDevices.equals(observedDevices)) {
            throw new IllegalArgumentException(
                    "host resource evidence must observe every capacity-bearing DeviceId exactly once");
        }
    }
}
