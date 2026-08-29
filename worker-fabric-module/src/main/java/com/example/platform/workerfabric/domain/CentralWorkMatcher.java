package com.example.platform.workerfabric.domain;

import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Correctness-only Native Pull matcher; global optimization remains Roadmap #23. */
public final class CentralWorkMatcher {

    private static final Comparator<EligibleNativeWork> STABLE_TASK_ID_ORDER =
            Comparator.comparing(candidate -> candidate.candidate().executableTask().id());

    private final AtomicAssignmentGrantBoundary grantBoundary;

    public CentralWorkMatcher(AtomicAssignmentGrantBoundary grantBoundary) {
        this.grantBoundary = Objects.requireNonNull(grantBoundary, "grantBoundary");
    }

    /**
     * Resolves one logical RequestWork. CAN_RUN is evaluated for every candidate before
     * WHICH_IS_BEST applies the stable task-identity ordering to the legal subset.
     */
    public RequestWorkResult match(
            RequestWork requestWork,
            RequestWorkValidationContext validationContext,
            Collection<PendingNativeWorkCandidate> pendingCandidates) {
        Objects.requireNonNull(requestWork, "requestWork");
        Objects.requireNonNull(validationContext, "validationContext");
        Objects.requireNonNull(pendingCandidates, "pendingCandidates");

        Optional<RequestWorkResult> prior = grantBoundary.findResolution(requestWork);
        if (prior.isPresent()) {
            return requireRequest(prior.orElseThrow(), requestWork.requestWorkId());
        }

        Optional<RequestWorkFailureReason> registrationFailure =
                grantBoundary.validateRegistration(requestWork);
        if (registrationFailure.isPresent()) {
            return resolveTerminal(
                    requestWork,
                    new RequestWorkResult.Rejected(
                            requestWork.requestWorkId(), registrationFailure.orElseThrow()));
        }

        Optional<RequestWorkResult> validationFailure =
                validateRequest(requestWork, validationContext);
        if (validationFailure.isPresent()) {
            return resolveTerminal(requestWork, validationFailure.orElseThrow());
        }

        List<EligibleNativeWork> canRun = new ArrayList<>();
        for (PendingNativeWorkCandidate candidate : pendingCandidates) {
            Objects.requireNonNull(candidate, "pendingCandidates element");
            evaluateCanRun(requestWork, validationContext, candidate).ifPresent(canRun::add);
        }

        Optional<EligibleNativeWork> selected = whichIsBest(canRun);
        if (selected.isEmpty()) {
            return resolveTerminal(
                    requestWork,
                    new RequestWorkResult.NoWork(requestWork.requestWorkId()));
        }

        EligibleNativeWork chosen = selected.orElseThrow();
        AtomicAssignmentGrantCommand command = new AtomicAssignmentGrantCommand(
                requestWork,
                chosen.candidate().executableTask(),
                chosen.backendSelection(),
                chosen.runtimeEligibilityDecision(),
                chosen.candidate().resourceDemand(),
                validationContext.authoritativeHostResourceSnapshot(),
                validationContext.authoritativeSchedulableCapacity());
        RequestWorkResult result = requireRequest(
                grantBoundary.tryGrant(command), requestWork.requestWorkId());
        if (result instanceof RequestWorkResult.Granted granted
                && !granted.grant().executableTaskId().equals(
                        chosen.candidate().executableTask().id())) {
            throw new IllegalStateException(
                    "Task D grant result does not bind the task selected by CentralWorkMatcher");
        }
        return result;
    }

    private static Optional<RequestWorkResult> validateRequest(
            RequestWork requestWork,
            RequestWorkValidationContext context) {
        if (!requestWork.workerRuntimeId().equals(context.workerRuntime().id())) {
            return rejected(requestWork, RequestWorkFailureReason.WORKER_RUNTIME_NOT_REGISTERED);
        }
        LocalWorkerRuntimeIncarnationBinding binding = context.runtimeHostBinding();
        if (!requestWork.workerRuntimeId().equals(binding.workerRuntimeId())) {
            return rejected(requestWork, RequestWorkFailureReason.WORKER_RUNTIME_NOT_REGISTERED);
        }
        if (!requestWork.workerRuntimeIncarnationId().equals(
                binding.workerRuntimeIncarnationId())) {
            return rejected(requestWork, RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH);
        }
        if (!requestWork.workerRuntimeAvailability().equals(
                context.authoritativeWorkerRuntimeAvailability())) {
            return rejected(requestWork, RequestWorkFailureReason.WORKER_RUNTIME_UNHEALTHY);
        }
        if (!requestWork.physicalHostId().equals(binding.physicalHostId())) {
            return rejected(requestWork, RequestWorkFailureReason.HOST_INCARNATION_MISMATCH);
        }
        if (!requestWork.physicalHostIncarnationId().equals(
                binding.physicalHostIncarnationId())) {
            return rejected(requestWork, RequestWorkFailureReason.HOST_INCARNATION_MISMATCH);
        }
        if (!requestWork.workerRuntimeAvailability().isReachable()) {
            return rejected(requestWork, RequestWorkFailureReason.WORKER_RUNTIME_UNHEALTHY);
        }
        if (!context.hostAvailability().isReachable()) {
            return rejected(requestWork, RequestWorkFailureReason.PHYSICAL_HOST_UNAVAILABLE);
        }
        if (!requestWork.hostResourceSnapshot().equals(
                context.authoritativeHostResourceSnapshot())) {
            return Optional.of(new RequestWorkResult.ReprobeRequired(
                    requestWork.requestWorkId(),
                    RequestWorkFailureReason.HOST_RESOURCE_SNAPSHOT_MISMATCH));
        }

        HostResourceSnapshotFreshness freshness = context.snapshotFreshnessPolicy().assess(
                Optional.of(requestWork.hostResourceSnapshot()),
                context.hostAvailability(),
                context.evaluatedAt());
        if (freshness.requiresReprobe()) {
            return Optional.of(new RequestWorkResult.ReprobeRequired(
                    requestWork.requestWorkId(),
                    RequestWorkFailureReason.STALE_HOST_RESOURCE_SNAPSHOT));
        }
        if (!freshness.permitsAssignment()) {
            return Optional.of(new RequestWorkResult.ReprobeRequired(
                    requestWork.requestWorkId(),
                    RequestWorkFailureReason.UNKNOWN_RUNTIME_ELIGIBILITY));
        }
        if (!context.authoritativeSchedulableCapacity().available()) {
            return rejected(
                    requestWork,
                    RequestWorkFailureReason.RESERVATION_RECONCILIATION_REQUIRED);
        }
        if (requestWork.workerDerivedSchedulableCapacity().isPresent()
                && !requestWork.workerDerivedSchedulableCapacity().orElseThrow().equals(
                        context.authoritativeSchedulableCapacity())) {
            return rejected(
                    requestWork,
                    RequestWorkFailureReason.RESERVATION_RECONCILIATION_REQUIRED);
        }
        return Optional.empty();
    }

    private static Optional<EligibleNativeWork> evaluateCanRun(
            RequestWork requestWork,
            RequestWorkValidationContext context,
            PendingNativeWorkCandidate candidate) {
        if (!candidate.pendingWithoutActiveLease()) {
            return Optional.empty();
        }

        ExecutionBackendEligibilityDecision backendDecision =
                ExecutionBackendEligibilityEvaluator.evaluate(
                        candidate.executableTask(),
                        candidate.backendExecutionSupport(),
                        ExecutionBackend.NATIVE_PULL_WORKER);
        if (!backendDecision.eligible()) {
            return Optional.empty();
        }
        ExecutionBackendSelection backendSelection = ExecutionBackendSelection.select(
                candidate.providerBoundGraph(), candidate.executableTask(), backendDecision);

        Optional<DeviceDescriptor> device = selectedDevice(context, candidate.resourceDemand());
        Optional<DeviceAvailability> deviceAvailability = device.flatMap(descriptor ->
                Optional.ofNullable(requestWork.deviceAvailability().get(descriptor.id())));
        List<StaticProviderCompatibilityProof> staticCompatibilityProofs = candidate
                .executableTask().memberships().stream()
                .map(membership -> candidate.providerBoundGraph().providerFeasibilityView()
                        .requireStaticallyFeasible(
                                membership.physicalPlanUnit(),
                                candidate.staticallyCompatibleProviderCandidate()))
                .toList();
        NativeRuntimeEligibilityRequest eligibilityRequest = new NativeRuntimeEligibilityRequest(
                candidate.providerBoundGraph(),
                candidate.executableTask(),
                candidate.staticallyCompatibleProviderCandidate(),
                staticCompatibilityProofs,
                candidate.providerHardwareRequirement(),
                candidate.runtimeDependencyRequirements(),
                requestWork.providerHardwareObservation(),
                requestWork.runtimeDependencyObservation(),
                backendSelection,
                Optional.of(context.workerRuntime()),
                Optional.of(context.authoritativeWorkerRuntimeAvailability()),
                Optional.of(context.runtimeHostBinding()),
                Optional.of(context.physicalHost()),
                Optional.of(context.hostAvailability()),
                Optional.of(context.authoritativeHostResourceSnapshot()),
                context.snapshotFreshnessPolicy(),
                context.evaluatedAt(),
                device,
                deviceAvailability,
                Optional.of(context.authoritativeSchedulableCapacity()),
                candidate.resourceDemand(),
                candidate.authoritativeReservationFeasibility(),
                requestWork.runtimeEnvironmentAvailability(),
                candidate.sandboxRequirement(),
                requestWork.sandboxRuntimeAvailability(),
                requestWork.runtimeSupportAdvertisement(),
                candidate.runtimeSupportRequirement(),
                candidate.providerProbeRequirement(),
                candidate.providerProbeResult());
        RuntimeEligibilityDecision runtimeDecision =
                RuntimeEligibilityEvaluator.evaluate(eligibilityRequest);
        if (!runtimeDecision.eligible()) {
            return Optional.empty();
        }
        return Optional.of(new EligibleNativeWork(candidate, backendSelection, runtimeDecision));
    }

    private static Optional<DeviceDescriptor> selectedDevice(
            RequestWorkValidationContext context,
            RuntimeResourceDemand demand) {
        if (demand.deviceDemands().size() != 1) {
            return Optional.empty();
        }
        DeviceId required = demand.deviceDemands().keySet().iterator().next();
        return context.physicalHost().devices().stream()
                .filter(candidate -> candidate.id().equals(required))
                .findFirst();
    }

    private static Optional<EligibleNativeWork> whichIsBest(List<EligibleNativeWork> canRun) {
        List<EligibleNativeWork> stable = new ArrayList<>(canRun);
        stable.sort(STABLE_TASK_ID_ORDER);
        for (int index = 1; index < stable.size(); index++) {
            ExecutableTaskId previous = stable.get(index - 1).candidate().executableTask().id();
            ExecutableTaskId current = stable.get(index).candidate().executableTask().id();
            if (previous.equals(current)) {
                throw new IllegalArgumentException(
                        "pending Native Pull candidates contain duplicate ExecutableTaskId");
            }
        }
        return stable.stream().findFirst();
    }

    private RequestWorkResult resolveTerminal(
            RequestWork requestWork,
            RequestWorkResult terminal) {
        if (terminal instanceof RequestWorkResult.Granted) {
            throw new IllegalArgumentException("terminal RequestWork result cannot be a grant");
        }
        return requireRequest(
                grantBoundary.resolveTerminal(requestWork, terminal),
                requestWork.requestWorkId());
    }

    private static Optional<RequestWorkResult> rejected(
            RequestWork requestWork,
            RequestWorkFailureReason reason) {
        return Optional.of(new RequestWorkResult.Rejected(requestWork.requestWorkId(), reason));
    }

    private static RequestWorkResult requireRequest(
            RequestWorkResult result,
            RequestWorkId expectedRequestWorkId) {
        Objects.requireNonNull(result, "grant boundary result");
        if (!expectedRequestWorkId.equals(result.requestWorkId())) {
            throw new IllegalStateException(
                    "grant boundary result does not bind the resolving RequestWorkId");
        }
        return result;
    }

    private record EligibleNativeWork(
            PendingNativeWorkCandidate candidate,
            ExecutionBackendSelection backendSelection,
            RuntimeEligibilityDecision runtimeEligibilityDecision) {}
}
