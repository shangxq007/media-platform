package com.example.platform.workerfabric.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * P2-5b-2a-1-2-R3a: bounded V1 host/runtime registration loop for the local single host.
 *
 * <p>Each tick observes the host through the {@link HostResourceAgent.ResourceProbe}, publishes the
 * exact evidence as a {@link HostResourceSnapshot}, derives {@link SchedulableCapacity} for the local
 * runtime, and (re-)registers the host and its worker runtime with a bounded validity window.
 *
 * <p>Owner decisions applied: validity 30 s with a 10 s cadence and a worker-local timer; safety
 * headroom = CPU {@code max(1 core, 10%)}, memory 10 %, temporary storage 10 % with **floor**
 * rounding; identities {@code local} / {@code local} / {@code local-ffmpeg} / {@code local-ffmpeg-v1};
 * snapshot generation stays {@code first()} for bounded V1 (never advanced per tick).
 *
 * <p><b>Idempotent</b>: the incarnation identity is a stable fingerprint of the host identity and its
 * static capacity, so repeated ticks update the same durable registration row instead of minting new
 * incarnations.
 *
 * <p><b>Fail closed</b>: an observation failure propagates
 * ({@link HostObservationUnavailableException}) and the tick registers nothing; the previous
 * registration then expires on its own validity window.
 */
public final class LocalHostRegistrationLoop {

    public static final String HOST_LOCATION = "local";
    public static final String TRUST_ZONE = "local";
    public static final String WORKER_RUNTIME_ID = "local-ffmpeg";
    public static final String WORKER_RUNTIME_INCARNATION_ID = "local-ffmpeg-v1";

    public static final Duration REGISTRATION_VALIDITY = Duration.ofSeconds(30);
    public static final Duration TICK_CADENCE = Duration.ofSeconds(10);
    /** Minimum CPU headroom: one full core in millicores. */
    public static final long MINIMUM_CPU_HEADROOM_MILLICORES = 1000L;
    /** Proportional headroom for CPU (when larger than one core), memory and temporary storage. */
    public static final double HEADROOM_RATIO = 0.10d;

    private final HostResourceAgent.ResourceProbe probe;
    private final WorkerFabricRegistrationBoundary registrationBoundary;
    private final Clock clock;
    private final PhysicalHostId physicalHostId;
    private final HostLocation location = HostLocation.of(HOST_LOCATION);
    private final TrustZoneId trustZone = TrustZoneId.of(TRUST_ZONE);
    private final WorkerRuntimeId workerRuntimeId = WorkerRuntimeId.of(WORKER_RUNTIME_ID);
    private final WorkerRuntimeIncarnationId workerRuntimeIncarnationId =
            WorkerRuntimeIncarnationId.of(WORKER_RUNTIME_INCARNATION_ID);
    private final HostResourceSnapshotFreshnessPolicy freshnessPolicy =
            new HostResourceSnapshotFreshnessPolicy(
                    REGISTRATION_VALIDITY, HostResourceSnapshotSchemaVersion.CURRENT);

    public LocalHostRegistrationLoop(
            HostResourceAgent.ResourceProbe probe,
            WorkerFabricRegistrationBoundary registrationBoundary,
            Clock clock,
            PhysicalHostId physicalHostId) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.registrationBoundary = Objects.requireNonNull(registrationBoundary, "registrationBoundary");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.physicalHostId = Objects.requireNonNull(physicalHostId, "physicalHostId");
    }

    /** One registration tick: observe, publish, derive capacity, register host + runtime. */
    public void registerOnce() {
        PhysicalHostDescriptor descriptor = probe.fingerprintStaticHostResources(physicalHostId);
        CapacitySnapshot staticCapacity = probe.collectStaticCapacity(descriptor);
        ObservedUsage observedUsage = probe.collectHostAndDeviceObservation(descriptor);
        PhysicalHostIncarnationId incarnationId =
                PhysicalHostIncarnationId.of(stableIncarnationFingerprint(staticCapacity));
        Instant now = clock.instant();
        HostResourceSnapshot snapshot = new HostResourceSnapshot(
                physicalHostId,
                incarnationId,
                HostResourceSnapshotGeneration.first(),
                now,
                HostResourceSnapshotSchemaVersion.CURRENT,
                staticCapacity,
                observedUsage,
                Optional.empty());
        SafetyHeadroom safetyHeadroom = headroomFor(staticCapacity);

        registrationBoundary.registerHost(new WorkerFabricRegistrationBoundary.HostRegistration(
                physicalHostId, incarnationId, snapshot, safetyHeadroom,
                now, now.plus(REGISTRATION_VALIDITY)));
        registrationBoundary.registerRuntime(new WorkerFabricRegistrationBoundary.RuntimeRegistration(
                workerRuntimeId, workerRuntimeIncarnationId, physicalHostId, incarnationId,
                now, now.plus(REGISTRATION_VALIDITY)));

        SchedulableCapacity capacity = SchedulableCapacity.forLocalRuntime(
                snapshot,
                List.<Reservation>of(),
                safetyHeadroom,
                new PhysicalHostAvailability(physicalHostId, incarnationId, AvailabilityState.REACHABLE),
                new WorkerRuntimeAvailability(
                        workerRuntimeId, workerRuntimeIncarnationId, AvailabilityState.REACHABLE),
                new LocalWorkerRuntimeIncarnationBinding(
                        workerRuntimeId, workerRuntimeIncarnationId, physicalHostId, incarnationId),
                new WorkerRuntimeDescriptor(
                        workerRuntimeId, RuntimeLifecycleKind.RESIDENT_RUNTIME, Optional.of(physicalHostId)),
                freshnessPolicy,
                now);
        if (!capacity.available()) {
            throw new IllegalStateException(
                    "bounded local host has no schedulable capacity: " + capacity.disposition());
        }
    }

    /** Owner decision Q2 with floor rounding; never exceeds the observed static capacity. */
    public static SafetyHeadroom headroomFor(CapacitySnapshot capacity) {
        Objects.requireNonNull(capacity, "capacity");
        long cpuMillicores = Math.max(
                MINIMUM_CPU_HEADROOM_MILLICORES,
                (long) Math.floor(capacity.cpu().millicores() * HEADROOM_RATIO));
        long memoryBytes = (long) Math.floor(capacity.memory().bytes() * HEADROOM_RATIO);
        long temporaryStorageBytes =
                (long) Math.floor(capacity.temporaryStorage().bytes() * HEADROOM_RATIO);
        return new SafetyHeadroom(new ReservedResources(
                cpuMillicores, memoryBytes, temporaryStorageBytes, Map.of()));
    }

    /**
     * Stable incarnation fingerprint: SHA-256 over the host identity, configured location/trust zone
     * and the observed static capacity. Deterministic — no wall-clock, no random, no per-process value.
     */
    public String stableIncarnationFingerprint(CapacitySnapshot capacity) {
        String canonical = String.join("\u0000",
                physicalHostId.value(),
                location.value(),
                trustZone.value(),
                Long.toString(capacity.cpu().millicores()),
                Long.toString(capacity.memory().bytes()),
                Long.toString(capacity.temporaryStorage().bytes()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
