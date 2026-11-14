package com.example.platform.runtime.mediatask;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.providerplugin.PendingNativeWorkProjection;
import com.example.platform.providerplugin.PendingNativeWorkProjectionException;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.AvailabilityState;
import com.example.platform.workerfabric.domain.CapacitySnapshot;
import com.example.platform.workerfabric.domain.CpuCapacity;
import com.example.platform.workerfabric.domain.DeviceDescriptor;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceModel;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.DeviceVendor;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.HostResourceSnapshotFreshnessPolicy;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.HostResourceSnapshotSchemaVersion;
import com.example.platform.workerfabric.domain.LocalHostRegistrationLoop;
import com.example.platform.workerfabric.domain.LocalWorkerRuntimeIncarnationBinding;
import com.example.platform.workerfabric.domain.MemoryCapacity;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.domain.ObservedCpuUsage;
import com.example.platform.workerfabric.domain.ObservedDeviceUsage;
import com.example.platform.workerfabric.domain.ObservedMemoryUsage;
import com.example.platform.workerfabric.domain.ObservedTemporaryStorageUsage;
import com.example.platform.workerfabric.domain.ObservedUsage;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.PhysicalHostAvailability;
import com.example.platform.workerfabric.domain.PhysicalHostDescriptor;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkId;
import com.example.platform.workerfabric.domain.RequestWorkResult;
import com.example.platform.workerfabric.domain.RequestWorkValidationContext;
import com.example.platform.workerfabric.domain.ReservationFeasibility;
import com.example.platform.workerfabric.domain.ReservationFeasibilityQuery;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.RuntimeResourceDemand;
import com.example.platform.workerfabric.domain.SafetyHeadroom;
import com.example.platform.workerfabric.domain.SchedulableCapacity;
import com.example.platform.workerfabric.domain.TaskResourceDemandDeriver;
import com.example.platform.workerfabric.domain.TemporaryStorageCapacity;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary.PersistedHostDescriptor;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary.PersistedHostRegistration;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary.PersistedHostSnapshot;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary.PersistedObservedUsage;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary.PersistedRuntimeRegistration;
import com.example.platform.workerfabric.domain.WorkerReadinessEvidence;
import com.example.platform.workerfabric.domain.WorkerRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerRuntimeDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * First Render minimal grant minter: the one authorised production caller of Native Pull admission.
 *
 * <p>When {@link MediaTaskActivity} finds no current grant for a task it delegates here. The minter
 * reads the authoritative persisted registration facts (host/runtime registration, host descriptor,
 * host resource snapshot including observed usage, snapshot generation) and the
 * WORKER_READINESS_EVIDENCE_V1 record, reconstructs the canonical context, and — using the task's
 * actual bound graph and the exact matching provider contribution — drives
 * {@code PendingNativeWorkProjection.projectAll(...)} plus the existing
 * {@code NativePullAdmissionPort.admit(...)}.
 *
 * <p><b>Fail-closed by design.</b> Nothing is fabricated: a missing, expired, incarnation-mismatched
 * or generation-mismatched authority yields {@link Result.Unavailable} / {@link Result.Rejected}, and
 * a mint is reported only after admission granted AND the durable grant was re-read through
 * {@code findCurrentGrant(taskId)}. The coarse availability passed to admission is derived from the
 * persisted readiness evidence itself (never from a static default), so the existing consistency rule
 * in {@code WorkerSandboxEligibilityResolver} holds.
 */
public final class MediaTaskGrantMinter {

    /** Native admission namespace; the framed digest keeps arbitrary owner identities bounded. */
    public static final String REQUEST_WORK_ID_PREFIX = "rw:";

    /**
     * Bounded-V1 snapshot freshness: the evidence is never older than the registration validity
     * window, matching {@code LocalHostRegistrationLoop} cadence and validity.
     */
    private static final HostResourceSnapshotFreshnessPolicy SNAPSHOT_FRESHNESS_POLICY =
            new HostResourceSnapshotFreshnessPolicy(
                    LocalHostRegistrationLoop.REGISTRATION_VALIDITY,
                    HostResourceSnapshotSchemaVersion.CURRENT);

    private final WorkerFabricRegistrationBoundary registrationBoundary;
    private final AtomicAssignmentGrantBoundary grantBoundary;
    private final NativePullAdmissionPort admissionPort;
    private final AdmissionEvidenceProducer evidenceProducer;
    private final ReservationFeasibilityQuery reservationFeasibilityQuery;
    private final Clock clock;
    private final WorkerRuntimeIncarnationId localIncarnation;

    public MediaTaskGrantMinter(
            WorkerFabricRegistrationBoundary registrationBoundary,
            AtomicAssignmentGrantBoundary grantBoundary,
            NativePullAdmissionPort admissionPort,
            AdmissionEvidenceProducer evidenceProducer,
            ReservationFeasibilityQuery reservationFeasibilityQuery,
            Clock clock, WorkerRuntimeIncarnationId localIncarnation) {
        this.registrationBoundary = Objects.requireNonNull(
                registrationBoundary, "registrationBoundary");
        this.grantBoundary = Objects.requireNonNull(grantBoundary, "grantBoundary");
        this.admissionPort = Objects.requireNonNull(admissionPort, "admissionPort");
        this.evidenceProducer = Objects.requireNonNull(evidenceProducer, "evidenceProducer");
        this.reservationFeasibilityQuery = Objects.requireNonNull(
                reservationFeasibilityQuery, "reservationFeasibilityQuery");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.localIncarnation = Objects.requireNonNull(localIncarnation,"localIncarnation");
    }

    /** Never consume another process's grant merely because it is current in the shared database. */
    public void requireLocalGrant(AssignmentGrant grant) {
        var runtime=registrationBoundary.findActiveRuntimeRegistration()
                .orElseThrow(()->new IllegalStateException("local runtime registration absent"));
        if(!localIncarnation.equals(grant.assignment().workerRuntimeIncarnationId())
                || !localIncarnation.equals(runtime.workerRuntimeIncarnationId())
                || !runtime.validUntil().isAfter(clock.instant())) {
            throw new IllegalStateException("grant does not belong to the current local runtime incarnation");
        }
    }

    /** Bind the logical owner and exact task without exceeding the persisted 128-character limit. */
    public static RequestWorkId requestWorkId(String ownerIdentity, ExecutableTaskId taskId) {
        return requestWorkId(ownerIdentity, taskId, 1L);
    }

    /**
     * Deterministic RequestWork identity for one Temporal admission attempt. Attempt 1 retains the
     * original identity; later attempts append the activity attempt number so a fresh snapshot must
     * be represented by a fresh, traceable admission attempt rather than reused as the old context.
     */
    public static RequestWorkId requestWorkId(
            String ownerIdentity, ExecutableTaskId taskId, long admissionAttempt) {
        Objects.requireNonNull(ownerIdentity, "ownerIdentity");
        Objects.requireNonNull(taskId, "taskId");
        if (ownerIdentity.isBlank()) throw new IllegalArgumentException("ownerIdentity must not be blank");
        if (admissionAttempt < 1) throw new IllegalArgumentException("admissionAttempt must be positive");
        byte[] owner = ownerIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] task = taskId.sha256Hex().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var framed = java.nio.ByteBuffer.allocate(8 + owner.length + task.length
                + (admissionAttempt == 1 ? 0 : 8));
        framed.putInt(owner.length).put(owner).putInt(task.length).put(task);
        if (admissionAttempt != 1) {
            framed.putLong(admissionAttempt);
        }
        try {
            return RequestWorkId.of(REQUEST_WORK_ID_PREFIX + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(framed.array())));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("required SHA-256 unavailable", unavailable);
        }
    }

    /** Attempts to mint the grant for one task; never fabricates success. */
    public Result mint(String renderJobId, ExecutableTaskId taskId) {
        return mint(renderJobId, taskId, null, null);
    }

    /**
     * Attempts to mint the grant for one task using the actual bound graph task and the exact
     * matching provider contribution. Never fabricates success.
     */
    public Result mint(
            String renderJobId,
            ExecutableTaskId taskId,
            ProviderBoundExecutableTaskGraph graph,
            ProviderPluginContribution contribution) {
        return mint(renderJobId, taskId, graph, contribution, 1L);
    }

    /**
     * Attempts to mint the grant for one task using the actual bound graph task and the exact
     * matching provider contribution. The admission attempt is part of the RequestWork identity;
     * the logical render job and task identity remain stable.
     */
    public Result mint(
            String renderJobId,
            ExecutableTaskId taskId,
            ProviderBoundExecutableTaskGraph graph,
            ProviderPluginContribution contribution,
            long admissionAttempt) {
        Objects.requireNonNull(taskId, "taskId");
        RequestWorkId requestWorkId = requestWorkId(renderJobId, taskId, admissionAttempt);
        Instant now = clock.instant();

        var host = registrationBoundary.findActiveHostRegistration();
        var runtime = registrationBoundary.findActiveRuntimeRegistration();
        List<String> missingRegistration = new ArrayList<>();
        if (host.isEmpty()) {
            missingRegistration.add("active-host-registration");
        }
        if (runtime.isEmpty()) {
            missingRegistration.add("active-runtime-registration");
        }
        if (!missingRegistration.isEmpty()) {
            return new Result.Unavailable(requestWorkId, List.copyOf(missingRegistration));
        }

        PersistedHostRegistration hostRegistration = host.orElseThrow();
        PersistedRuntimeRegistration runtimeRegistration = runtime.orElseThrow();
        if (!localIncarnation.equals(runtimeRegistration.workerRuntimeIncarnationId())) {
            return new Result.Rejected(requestWorkId,"LOCAL_RUNTIME_INCARNATION_RETIRED");
        }
        if (!hostRegistration.validUntil().isAfter(now)) {
            return new Result.Rejected(requestWorkId, "HOST_REGISTRATION_EXPIRED");
        }
        if (!runtimeRegistration.validUntil().isAfter(now)) {
            return new Result.Rejected(requestWorkId, "RUNTIME_REGISTRATION_EXPIRED");
        }

        PhysicalHostId physicalHostId = hostRegistration.physicalHostId();
        PhysicalHostIncarnationId physicalHostIncarnationId =
                hostRegistration.physicalHostIncarnationId();
        WorkerRuntimeId workerRuntimeId = runtimeRegistration.workerRuntimeId();
        WorkerRuntimeIncarnationId workerRuntimeIncarnationId =
                runtimeRegistration.workerRuntimeIncarnationId();
        if (!physicalHostId.equals(runtimeRegistration.physicalHostId())
                || !physicalHostIncarnationId.equals(
                        runtimeRegistration.physicalHostIncarnationId())) {
            return new Result.Rejected(requestWorkId, "RUNTIME_HOST_INCARNATION_MISMATCH");
        }

        var generation = registrationBoundary.currentSnapshotGeneration(
                physicalHostId, physicalHostIncarnationId);
        if (generation.isEmpty()) {
            return new Result.Unavailable(
                    requestWorkId, List.of("host-snapshot-generation-authority"));
        }
        HostResourceSnapshotGeneration snapshotGeneration = generation.orElseThrow();

        // WORKER_READINESS_EVIDENCE_V1: the authoritative readiness record for this exact host
        // incarnation. Missing, expired, incarnation-mismatched or generation-mismatched evidence
        // fails closed; UNKNOWN is never promoted to AVAILABLE.
        var readiness = registrationBoundary.findReadiness(
                physicalHostId, physicalHostIncarnationId);
        if (readiness.isEmpty()) {
            return new Result.Unavailable(requestWorkId, List.of("worker-readiness-evidence"));
        }
        WorkerReadinessEvidence evidence = readiness.orElseThrow();
        if (!evidence.isFresh(now)) {
            return new Result.Rejected(requestWorkId, "READINESS_EVIDENCE_EXPIRED");
        }
        if (!workerRuntimeId.equals(evidence.workerRuntimeId())
                || !workerRuntimeIncarnationId.equals(evidence.workerRuntimeIncarnationId())) {
            return new Result.Rejected(
                    requestWorkId, "READINESS_RUNTIME_INCARNATION_MISMATCH");
        }
        if (!snapshotGeneration.equals(evidence.snapshotGeneration())) {
            return new Result.Rejected(requestWorkId, "READINESS_GENERATION_MISMATCH");
        }

        var descriptor = registrationBoundary.findHostDescriptor(physicalHostId);
        if (descriptor.isEmpty()) {
            return new Result.Unavailable(requestWorkId, List.of("physical-host-descriptor"));
        }
        var persistedSnapshot = registrationBoundary.findHostSnapshot(
                physicalHostId, physicalHostIncarnationId, snapshotGeneration);
        if (persistedSnapshot.isEmpty()) {
            return new Result.Unavailable(
                    requestWorkId, List.of("persisted-host-resource-snapshot"));
        }
        if (persistedSnapshot.orElseThrow().observedUsage().isEmpty()) {
            return new Result.Unavailable(
                    requestWorkId, List.of("host-resource-snapshot.observed-usage"));
        }
        if (graph == null || contribution == null) {
            return new Result.Unavailable(
                    requestWorkId, List.of("bound-graph-and-provider-contribution"));
        }

        List<PendingNativeWorkCandidate> candidates;
        // P1-1A.5: read-only pre-admission reservation feasibility (conservative filter). The final
        // Grant remains the in-transaction JooqAtomicAssignmentGrantBoundary decision; a read failure
        // or missing/!matching authority yields UNKNOWN, which the evaluator still fails closed on.
        ReservationFeasibility reservationFeasibility = TaskResourceDemandDeriver
                .derive(contribution.resourceProfile())
                .map(demand -> reservationFeasibilityQuery.assess(
                        physicalHostId, physicalHostIncarnationId, snapshotGeneration, demand))
                .orElse(ReservationFeasibility.UNKNOWN);
        try {
            // P1-1A.7 per-task admission fix: scope this mint's candidate to the REQUESTED task. The
            // matcher selects the best eligible candidate by stable task-id order, so projecting the
            // whole graph would make the second per-task mint re-select the already-granted (now
            // non-claimable) first task and fail with ADMISSION_NO_WORK. The requested task id comes
            // from the caller's exact bound-graph task; a request id that is not in the graph fails
            // closed instead of falling back to any other candidate.
            ExecutableTask requestedTask = graph.tasks().stream()
                    .filter(candidate -> candidate.id().equals(taskId))
                    .findFirst()
                    .orElse(null);
            if (requestedTask == null) {
                return new Result.Unavailable(
                        requestWorkId, List.of("requested-task-not-in-bound-graph"));
            }
            candidates = List.of(PendingNativeWorkProjection.project(
                    requestedTask, graph, contribution, reservationFeasibility));
        } catch (PendingNativeWorkProjectionException unprojectable) {
            return new Result.Unavailable(
                    requestWorkId,
                    List.of("pending-native-work-candidates: " + unprojectable.getMessage()));
        }
        if (candidates.isEmpty()) {
            return new Result.Rejected(requestWorkId, "ADMISSION_NO_WORK");
        }

        // P1-1A.6 (Gate 4): the three independent admission inputs, each from its own authority. A
        // signal that cannot be produced is named and fails closed; one input never stands in for
        // another and nothing is defaulted.
        AdmissionEvidenceProducer.Evidence admissionEvidence = evidenceProducer.produce(
                workerRuntimeId,
                physicalHostId,
                contribution.providerBindingPin().providerImplementationId());
        List<String> missingEvidence = new ArrayList<>();
        if (admissionEvidence.runtimeSupportAdvertisement().isEmpty()) {
            missingEvidence.add("runtime-support-advertisement");
        }
        if (admissionEvidence.providerHardwareObservation().isEmpty()) {
            missingEvidence.add("provider-hardware-observation");
        }
        if (admissionEvidence.runtimeDependencyObservation().isEmpty()) {
            missingEvidence.add("runtime-dependency-observation");
        }
        if (!missingEvidence.isEmpty()) {
            return new Result.Unavailable(requestWorkId, List.copyOf(missingEvidence));
        }
        WorkerRuntimeSupportAdvertisement advertisement =
                admissionEvidence.runtimeSupportAdvertisement().orElseThrow();

        // P1-1A.5 (evaluation timestamp ordering fix): the admission evidence just stamped its
        // hardware/runtime-dependency observations with `observedAt = clock.instant()` at production
        // time. Re-take the evaluation timestamp AFTER evidence production so the eligibility
        // `evaluatedAt` (and the freshness/capacity context derived from it) is never earlier than the
        // just-produced observations; otherwise RuntimeEligibilityEvaluator reports
        // PROVIDER_HARDWARE_STALE_OBSERVATION / RUNTIME_DEPENDENCY_STALE_OBSERVATION (observedAt >
        // evaluatedAt) and CentralWorkMatcher returns NoWork. Freshness rules and fail-closed
        // semantics are unchanged; only the timestamp acquisition order is corrected.
        now = clock.instant();

        PersistedHostSnapshot snapshotRow = persistedSnapshot.orElseThrow();
        HostResourceSnapshot snapshot = canonicalSnapshot(snapshotRow);
        PhysicalHostDescriptor physicalHost = canonicalDescriptor(descriptor.orElseThrow());
        SafetyHeadroom safetyHeadroom = new SafetyHeadroom(new ReservedResources(
                snapshotRow.safetyHeadroomCpuMillicores(),
                snapshotRow.safetyHeadroomMemoryBytes(),
                snapshotRow.safetyHeadroomTemporaryStorageBytes(),
                Map.of()));
        PhysicalHostAvailability hostAvailability = new PhysicalHostAvailability(
                physicalHostId, physicalHostIncarnationId, AvailabilityState.REACHABLE);
        WorkerRuntimeAvailability runtimeAvailability = new WorkerRuntimeAvailability(
                workerRuntimeId, workerRuntimeIncarnationId, AvailabilityState.REACHABLE);
        LocalWorkerRuntimeIncarnationBinding binding = new LocalWorkerRuntimeIncarnationBinding(
                workerRuntimeId, workerRuntimeIncarnationId, physicalHostId, physicalHostIncarnationId);
        // P1-1A.6 (Gate 0): the runtime's frozen RuntimeLifecycleKind is the one its exact provider
        // requirement declares (FFmpeg = EPHEMERAL_TASK). The descriptor mirrors the advertisement so
        // descriptor == advertisement == requirement, which is exactly what the existing
        // RuntimeSupportAdvertisementEvaluator requires. The value is never hardcoded independently.
        WorkerRuntimeDescriptor runtimeDescriptor = WorkerRuntimeDescriptor.local(
                workerRuntimeId, advertisement.runtimeKind(), physicalHostId);
        SchedulableCapacity capacity = SchedulableCapacity.forLocalRuntime(
                snapshot, List.of(), safetyHeadroom, hostAvailability, runtimeAvailability,
                binding, runtimeDescriptor, SNAPSHOT_FRESHNESS_POLICY, now);

        // The coarse availability values come from the same authoritative readiness evidence; the
        // existing WorkerSandboxEligibilityResolver consistency rule (coarse == evidence) therefore
        // holds rather than being bypassed.
        RequestWork requestWork = new RequestWork(
                requestWorkId,
                workerRuntimeId,
                workerRuntimeIncarnationId,
                physicalHostId,
                physicalHostIncarnationId,
                snapshot,
                runtimeAvailability,
                Map.of(),
                evidence.environmentReadiness(),
                evidence.sandboxReadiness(),
                admissionEvidence.providerHardwareObservation(),
                admissionEvidence.runtimeDependencyObservation(),
                admissionEvidence.runtimeSupportAdvertisement(),
                Optional.empty());
        RequestWorkValidationContext context = new RequestWorkValidationContext(
                runtimeDescriptor,
                runtimeAvailability,
                binding,
                physicalHost,
                hostAvailability,
                snapshot,
                SNAPSHOT_FRESHNESS_POLICY,
                now,
                capacity);

        RequestWorkResult result = admissionPort.admit(requestWork, context, candidates);
        if (result instanceof RequestWorkResult.Granted) {
            var currentGrant = grantBoundary.findCurrentGrant(taskId);
            if (currentGrant.isPresent()) {
                return new Result.Minted(requestWorkId, currentGrant.orElseThrow());
            }
            return new Result.Unavailable(
                    requestWorkId, List.of("persisted-assignment-grant"));
        }
        if (result instanceof RequestWorkResult.CapacityUnavailable capacityUnavailable) {
            return new Result.Rejected(
                    requestWorkId, "ADMISSION_REJECTED: " + capacityUnavailable.reason().name());
        }
        if (result instanceof RequestWorkResult.ReprobeRequired reprobe) {
            return new Result.Rejected(
                    requestWorkId, "ADMISSION_REPROBE_REQUIRED: " + reprobe.reason().name());
        }
        if (result instanceof RequestWorkResult.Rejected rejected) {
            return new Result.Rejected(
                    requestWorkId, "ADMISSION_REJECTED: " + rejected.reason().name());
        }
        return new Result.Rejected(requestWorkId, "ADMISSION_NO_WORK");
    }

    /**
     * Reconstructs the canonical {@link HostResourceSnapshot} the persisted row published. Every
     * value comes from the durable projection; nothing is defaulted. Reporting-runtime telemetry is
     * not persisted, so it stays empty rather than being invented.
     */
    private static HostResourceSnapshot canonicalSnapshot(PersistedHostSnapshot row) {
        Map<DeviceId, DeviceResourceCapacity> deviceResources = new LinkedHashMap<>();
        row.devices().forEach(device -> {
            DeviceId deviceId = DeviceId.of(device.deviceId());
            deviceResources.put(deviceId, new DeviceResourceCapacity(
                    deviceId,
                    device.vramBytes(),
                    device.computeUnits(),
                    device.encoderEngines(),
                    device.decoderEngines()));
        });
        CapacitySnapshot staticCapacity = new CapacitySnapshot(
                CpuCapacity.ofMillicores(row.cpuMillicores()),
                MemoryCapacity.ofBytes(row.memoryBytes()),
                TemporaryStorageCapacity.ofBytes(row.temporaryStorageBytes()),
                deviceResources);

        PersistedObservedUsage observed = row.observedUsage().orElseThrow();
        Map<DeviceId, ObservedDeviceUsage> deviceUsage = new LinkedHashMap<>();
        observed.devices().forEach(device -> {
            DeviceId deviceId = DeviceId.of(device.deviceId());
            deviceUsage.put(deviceId, new ObservedDeviceUsage(
                    deviceId,
                    device.computeUtilizationRatio(),
                    device.vramUsedBytes(),
                    device.encoderUtilizationRatio(),
                    device.decoderUtilizationRatio()));
        });
        ObservedUsage observedUsage = new ObservedUsage(
                new ObservedCpuUsage(observed.cpuUtilizationRatio()),
                new ObservedMemoryUsage(observed.memoryUsedBytes()),
                new ObservedTemporaryStorageUsage(observed.temporaryStorageUsedBytes()),
                deviceUsage);

        return new HostResourceSnapshot(
                row.physicalHostId(),
                row.physicalHostIncarnationId(),
                row.snapshotGeneration(),
                row.capturedAt(),
                new HostResourceSnapshotSchemaVersion(row.schemaVersion()),
                staticCapacity,
                observedUsage,
                Optional.empty());
    }

    /** Reconstructs the canonical host descriptor the persisted authority published. */
    private static PhysicalHostDescriptor canonicalDescriptor(PersistedHostDescriptor row) {
        List<DeviceDescriptor> devices = row.devices().stream()
                .map(device -> new DeviceDescriptor(
                        DeviceId.of(device.deviceId()),
                        device.kind(),
                        DeviceVendor.of(device.vendor()),
                        DeviceModel.of(device.model())))
                .toList();
        return new PhysicalHostDescriptor(
                row.physicalHostId(), row.location(), row.trustZoneId(), devices);
    }

    /** Outcome of one mint attempt. Fail-closed variants never carry a grant. */
    public sealed interface Result permits Result.Minted, Result.Unavailable, Result.Rejected {

        /** A grant was minted by admission and re-read from the durable authority. */
        record Minted(RequestWorkId requestWorkId, AssignmentGrant grant) implements Result {
            public Minted {
                Objects.requireNonNull(requestWorkId, "requestWorkId");
                Objects.requireNonNull(grant, "grant");
            }
        }

        /** The authoritative inputs needed to build the context are absent. */
        record Unavailable(RequestWorkId requestWorkId, List<String> missingAuthoritativeInputs)
                implements Result {
            public Unavailable {
                Objects.requireNonNull(requestWorkId, "requestWorkId");
                missingAuthoritativeInputs =
                        List.copyOf(Objects.requireNonNull(
                                missingAuthoritativeInputs, "missingAuthoritativeInputs"));
            }
        }

        /** A present-but-invalid authority (for example an expired registration). */
        record Rejected(RequestWorkId requestWorkId, String reason) implements Result {
            public Rejected {
                Objects.requireNonNull(requestWorkId, "requestWorkId");
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
