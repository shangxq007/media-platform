package com.example.platform.workerfabric.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stage-2 mutable runtime evaluation for a statically legal selected Native Pull task. */
public final class RuntimeEligibilityEvaluator {

    private RuntimeEligibilityEvaluator() {}

    public static RuntimeEligibilityDecision evaluate(NativeRuntimeEligibilityRequest request) {
        Objects.requireNonNull(request, "request");
        EnumSet<RuntimeEligibilityReason> reasons = EnumSet.noneOf(RuntimeEligibilityReason.class);

        evaluateProbe(request, reasons);
        evaluateRuntimeSupportAdvertisement(request, reasons);
        evaluateWorkerAndHost(request, reasons);
        evaluateRuntimeMechanics(request, reasons);
        evaluateDevice(request, reasons);
        evaluateReservationAndResources(request, reasons);

        RuntimeEligibilityDecision.Status status;
        if (reasons.isEmpty()) {
            status = RuntimeEligibilityDecision.Status.ELIGIBLE;
        } else if (reasons.contains(RuntimeEligibilityReason.PROBE_UNKNOWN)
                || reasons.contains(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY)) {
            status = RuntimeEligibilityDecision.Status.UNKNOWN_FAIL_CLOSED;
        } else {
            status = RuntimeEligibilityDecision.Status.INELIGIBLE;
        }
        return new RuntimeEligibilityDecision(
                status,
                request.executableTask().id(),
                request.executableTask().providerBindingPin(),
                reasons.stream().toList());
    }

    private static void evaluateRuntimeSupportAdvertisement(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.runtimeSupportAdvertisement().isEmpty()
                && request.runtimeSupportRequirement().isEmpty()) {
            return;
        }
        if (request.workerRuntime().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH);
            return;
        }
        RuntimeSupportAdvertisementDecision decision =
                RuntimeSupportAdvertisementEvaluator.evaluate(
                        request.workerRuntime().orElseThrow(),
                        request.runtimeSupportAdvertisement(),
                        request.runtimeSupportRequirement());
        switch (decision.reason()) {
            case ACCEPTED_CANDIDATE_EVIDENCE -> { }
            case REQUIREMENT_MISSING ->
                    reasons.add(RuntimeEligibilityReason.RUNTIME_SUPPORT_REQUIREMENT_MISSING);
            case MISSING ->
                    reasons.add(RuntimeEligibilityReason.RUNTIME_SUPPORT_ADVERTISEMENT_MISSING);
            case RUNTIME_MISMATCH ->
                    reasons.add(RuntimeEligibilityReason.RUNTIME_SUPPORT_MISMATCH);
            case UNSUPPORTED ->
                    reasons.add(RuntimeEligibilityReason.RUNTIME_SUPPORT_UNSUPPORTED);
        }
    }

    private static void evaluateProbe(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.providerProbeRequirement() == ProviderProbeRequirement.NOT_REQUIRED) {
            return;
        }
        Optional<ProviderProbeResult> probe = request.providerProbeResult();
        if (probe.isEmpty() || probe.orElseThrow().status() == ProviderProbeResult.Status.UNKNOWN) {
            reasons.add(RuntimeEligibilityReason.PROBE_UNKNOWN);
        } else if (probe.orElseThrow().status() == ProviderProbeResult.Status.STALE) {
            reasons.add(RuntimeEligibilityReason.PROBE_STALE);
        } else if (probe.orElseThrow().status() == ProviderProbeResult.Status.FAILED) {
            reasons.add(RuntimeEligibilityReason.PROBE_FAILED);
        }
    }

    private static void evaluateWorkerAndHost(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.workerRuntime().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
            return;
        }
        WorkerRuntimeDescriptor worker = request.workerRuntime().orElseThrow();
        if (worker.lifecycleKind() == RuntimeLifecycleKind.REMOTE_RUNTIME
                || worker.physicalHostId().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
            return;
        }

        if (request.workerAvailability().isEmpty() || request.runtimeHostBinding().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
        } else {
            WorkerRuntimeAvailability availability = request.workerAvailability().orElseThrow();
            LocalWorkerRuntimeIncarnationBinding binding = request.runtimeHostBinding().orElseThrow();
            if (!worker.id().equals(availability.workerRuntimeId())
                    || !worker.id().equals(binding.workerRuntimeId())) {
                reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
            }
            if (!availability.isReachable()) {
                reasons.add(RuntimeEligibilityReason.WORKER_UNAVAILABLE);
            }
            if (!availability.incarnationId().equals(binding.workerRuntimeIncarnationId())) {
                reasons.add(RuntimeEligibilityReason.RUNTIME_INCARNATION_MISMATCH);
            }
        }

        if (request.physicalHost().isEmpty() || request.hostAvailability().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
            return;
        }
        PhysicalHostDescriptor host = request.physicalHost().orElseThrow();
        PhysicalHostAvailability hostAvailability = request.hostAvailability().orElseThrow();
        PhysicalHostId workerHost = worker.physicalHostId().orElseThrow();
        if (!workerHost.equals(host.id()) || !host.id().equals(hostAvailability.physicalHostId())) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
        }
        if (!hostAvailability.isReachable()) {
            reasons.add(RuntimeEligibilityReason.HOST_UNAVAILABLE);
        }

        request.runtimeHostBinding().ifPresent(binding -> {
            if (!binding.physicalHostId().equals(host.id())) {
                reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_WORKER);
            }
            if (!binding.physicalHostIncarnationId().equals(hostAvailability.incarnationId())) {
                reasons.add(RuntimeEligibilityReason.HOST_INCARNATION_MISMATCH);
            }
        });

        if (request.hostResourceSnapshot().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
            return;
        }
        HostResourceSnapshot snapshot = request.hostResourceSnapshot().orElseThrow();
        if (!snapshot.physicalHostId().equals(host.id())
                || !snapshot.physicalHostIncarnationId().equals(hostAvailability.incarnationId())) {
            reasons.add(RuntimeEligibilityReason.HOST_INCARNATION_MISMATCH);
            return;
        }
        HostResourceSnapshotFreshness freshness = request.snapshotFreshnessPolicy().assess(
                Optional.of(snapshot), hostAvailability, request.evaluatedAt());
        if (freshness.status() == HostResourceSnapshotFreshnessStatus.REPROBE_REQUIRED) {
            reasons.add(RuntimeEligibilityReason.STALE_HOST_RESOURCE_SNAPSHOT);
        } else if (freshness.status() == HostResourceSnapshotFreshnessStatus.NO_ASSIGNMENT) {
            reasons.add(RuntimeEligibilityReason.HOST_UNAVAILABLE);
        } else if (freshness.status() == HostResourceSnapshotFreshnessStatus.FAIL_CLOSED) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
        }
    }

    private static void evaluateRuntimeMechanics(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.runtimeEnvironmentAvailability() == RuntimeEnvironmentAvailability.UNAVAILABLE) {
            reasons.add(RuntimeEligibilityReason.RUNTIME_UNAVAILABLE);
        } else if (request.runtimeEnvironmentAvailability() == RuntimeEnvironmentAvailability.UNKNOWN) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
        }

        if (request.sandboxRequirement() == SandboxRuntimeRequirement.REQUIRED) {
            if (request.sandboxRuntimeAvailability() == SandboxRuntimeAvailability.UNAVAILABLE) {
                reasons.add(RuntimeEligibilityReason.SANDBOX_RUNTIME_UNAVAILABLE);
            } else if (request.sandboxRuntimeAvailability() == SandboxRuntimeAvailability.UNKNOWN) {
                reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
            }
        }
    }

    private static void evaluateDevice(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.resourceDemand().deviceDemands().isEmpty()) {
            return;
        }
        if (request.resourceDemand().deviceDemands().size() != 1
                || request.device().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE);
            return;
        }
        Map.Entry<DeviceId, RuntimeResourceDemand.DeviceDemand> required = request
                .resourceDemand().deviceDemands().entrySet().iterator().next();
        DeviceDescriptor device = request.device().orElseThrow();
        if (!required.getKey().equals(device.id())) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE);
            return;
        }
        if (request.physicalHost().isEmpty()
                || request.physicalHost().orElseThrow().devices().stream()
                        .noneMatch(candidate -> candidate.equals(device))) {
            reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE);
        }
        if (request.deviceAvailability().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
        } else {
            DeviceAvailability availability = request.deviceAvailability().orElseThrow();
            if (!availability.deviceId().equals(device.id())) {
                reasons.add(RuntimeEligibilityReason.NO_ELIGIBLE_DEVICE);
            } else if (!availability.isReachable()) {
                reasons.add(RuntimeEligibilityReason.DEVICE_UNAVAILABLE);
            }
        }
    }

    private static void evaluateReservationAndResources(
            NativeRuntimeEligibilityRequest request,
            EnumSet<RuntimeEligibilityReason> reasons) {
        if (request.reservationFeasibility() == ReservationFeasibility.CONFLICT) {
            reasons.add(RuntimeEligibilityReason.RESERVATION_CONFLICT);
        } else if (request.reservationFeasibility() == ReservationFeasibility.UNKNOWN) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
        }

        if (request.schedulableCapacity().isEmpty()) {
            reasons.add(RuntimeEligibilityReason.UNKNOWN_RUNTIME_ELIGIBILITY);
            return;
        }
        SchedulableCapacity capacity = request.schedulableCapacity().orElseThrow();
        if (request.hostResourceSnapshot().isPresent()) {
            HostResourceSnapshot snapshot = request.hostResourceSnapshot().orElseThrow();
            if (!capacity.physicalHostId().equals(snapshot.physicalHostId())
                    || !capacity.physicalHostIncarnationId().equals(
                            snapshot.physicalHostIncarnationId())) {
                reasons.add(RuntimeEligibilityReason.HOST_INCARNATION_MISMATCH);
                return;
            }
        }
        if (!capacity.available() || !fits(request.resourceDemand(), capacity)) {
            reasons.add(RuntimeEligibilityReason.INSUFFICIENT_CURRENT_RESOURCE);
        }
    }

    private static boolean fits(RuntimeResourceDemand demand, SchedulableCapacity capacity) {
        if (demand.cpuMillicores() > capacity.cpu().millicores()
                || demand.memoryBytes() > capacity.memory().bytes()
                || demand.temporaryStorageBytes() > capacity.temporaryStorage().bytes()) {
            return false;
        }
        for (RuntimeResourceDemand.DeviceDemand required : demand.deviceDemands().values()) {
            DeviceResourceCapacity available = capacity.deviceResources().get(required.deviceId());
            if (available == null
                    || required.vramBytes() > available.vramBytes()
                    || required.computeUnits() > available.computeUnits()
                    || required.encoderEngines() > available.encoderEngines()
                    || required.decoderEngines() > available.decoderEngines()) {
                return false;
            }
        }
        return true;
    }
}
