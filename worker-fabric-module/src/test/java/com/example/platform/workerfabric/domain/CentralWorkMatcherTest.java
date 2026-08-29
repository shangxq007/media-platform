package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class CentralWorkMatcherTest {

    @Test
    void nativePullRequiresCanonicalHardwareAndDependencyEvidenceBeforeGrant() {
        var runtime = TaskCTestFixture.runtime("i4-evidence");
        var work = TaskCTestFixture.candidate(20);
        RequestWork missingEvidence = withCanonicalObservations(
                runtime.requestWork(), Optional.empty(), Optional.empty());

        RequestWorkResult allGreen = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        runtime.requestWork(), runtime.context(), List.of(work.candidate()));
        RequestWorkResult unknown = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        missingEvidence, runtime.context(), List.of(work.candidate()));

        assertThat(allGreen).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(unknown).isInstanceOf(RequestWorkResult.NoWork.class);
    }

    @Test
    void native_pull_matcher_requires_matching_advertisement_for_provider_requirement() {
        var runtime = TaskCTestFixture.runtime("phase19-support");
        var work = TaskCTestFixture.candidate(19);
        RuntimeSupportIdentifier supportId = RuntimeSupportIdentifier.of("ffmpeg.cpu.transcode.v1");
        WorkerRuntimeSupportRequirement requirement = new WorkerRuntimeSupportRequirement(
                work.task().providerBindingPin(),
                runtime.context().workerRuntime().lifecycleKind(),
                supportId);
        PendingNativeWorkCandidate requiredCandidate = withSupportRequirement(
                work.candidate(), Optional.of(requirement));
        RequestWork matchingRequest = withAdvertisement(
                runtime.requestWork(),
                Optional.of(new WorkerRuntimeSupportAdvertisement(
                        runtime.requestWork().workerRuntimeId(),
                        runtime.context().workerRuntime().lifecycleKind(),
                        java.util.Map.of(supportId, new RuntimeSupportEvidence(
                                "provider-module", "ffmpeg-provider-module:v1")))));

        RequestWorkResult matching = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        matchingRequest, runtime.context(), List.of(requiredCandidate));
        RequestWorkResult missing = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        runtime.requestWork(), runtime.context(), List.of(requiredCandidate));
        RequestWorkResult advertisementAlone = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        matchingRequest, runtime.context(), List.of(work.candidate()));
        RequestWorkResult missingProbe = new CentralWorkMatcher(
                new TaskCTestFixture.RecordingGrantBoundary()).match(
                        matchingRequest,
                        runtime.context(),
                        List.of(withProbeResult(requiredCandidate, Optional.empty())));

        assertThat(matching).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(missing).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(advertisementAlone).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(missingProbe).isInstanceOf(RequestWorkResult.NoWork.class);
    }

    @Test
    void n1RequestWorkContainsNoCallerSelectedExecutableTaskId() {
        List<Class<?>> componentTypes = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
        List<String> componentNames = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentTypes).doesNotContain(ExecutableTaskId.class);
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("task")
                || name.toLowerCase().contains("queue")
                || name.toLowerCase().contains("priority")
                || name.toLowerCase().contains("fairness")
                || name.toLowerCase().contains("deadline"));
    }

    private static PendingNativeWorkCandidate withSupportRequirement(
            PendingNativeWorkCandidate candidate,
            Optional<WorkerRuntimeSupportRequirement> requirement) {
        return new PendingNativeWorkCandidate(
                candidate.providerBoundGraph(),
                candidate.executableTask(),
                candidate.staticallyCompatibleProviderCandidate(),
                candidate.providerHardwareRequirement(),
                candidate.runtimeDependencyRequirements(),
                candidate.backendExecutionSupport(),
                candidate.claimState(),
                candidate.resourceDemand(),
                candidate.authoritativeReservationFeasibility(),
                candidate.sandboxRequirement(),
                requirement,
                ProviderProbeRequirement.REQUIRED,
                Optional.of(new ProviderProbeResult(
                        candidate.executableTask().providerBindingPin(),
                        ProviderProbeResult.Status.HEALTHY)));
    }

    private static PendingNativeWorkCandidate withProbeResult(
            PendingNativeWorkCandidate candidate, Optional<ProviderProbeResult> probe) {
        return new PendingNativeWorkCandidate(
                candidate.providerBoundGraph(), candidate.executableTask(),
                candidate.staticallyCompatibleProviderCandidate(),
                candidate.providerHardwareRequirement(), candidate.runtimeDependencyRequirements(),
                candidate.backendExecutionSupport(), candidate.claimState(),
                candidate.resourceDemand(), candidate.authoritativeReservationFeasibility(),
                candidate.sandboxRequirement(), candidate.runtimeSupportRequirement(),
                ProviderProbeRequirement.REQUIRED, probe);
    }

    private static RequestWork withAdvertisement(
            RequestWork request,
            Optional<WorkerRuntimeSupportAdvertisement> advertisement) {
        return new RequestWork(
                request.requestWorkId(),
                request.workerRuntimeId(),
                request.workerRuntimeIncarnationId(),
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(),
                request.workerRuntimeAvailability(),
                request.deviceAvailability(),
                request.runtimeEnvironmentAvailability(),
                request.sandboxRuntimeAvailability(),
                request.providerHardwareObservation(),
                request.runtimeDependencyObservation(),
                advertisement,
                request.workerDerivedSchedulableCapacity());
    }

    private static RequestWork withCanonicalObservations(
            RequestWork request,
            Optional<ProviderHardwareObservation> hardwareObservation,
            Optional<RuntimeDependencyObservation> dependencyObservation) {
        return new RequestWork(
                request.requestWorkId(),
                request.workerRuntimeId(),
                request.workerRuntimeIncarnationId(),
                request.physicalHostId(),
                request.physicalHostIncarnationId(),
                request.hostResourceSnapshot(),
                request.workerRuntimeAvailability(),
                request.deviceAvailability(),
                request.runtimeEnvironmentAvailability(),
                request.sandboxRuntimeAvailability(),
                hardwareObservation,
                dependencyObservation,
                request.runtimeSupportAdvertisement(),
                request.workerDerivedSchedulableCapacity());
    }

    @Test
    void n2SameRequestWorkIdRetryReturnsSameGrantWithoutDuplicateGrant() {
        var runtime = TaskCTestFixture.runtime("n2");
        var work = TaskCTestFixture.candidate(2);
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();
        var matcher = new CentralWorkMatcher(boundary);

        RequestWorkResult first = matcher.match(
                runtime.requestWork(), runtime.context(), List.of(work.candidate()));
        RequestWorkResult retry = matcher.match(
                runtime.requestWork(), runtime.context(), List.of(work.candidate()));

        assertThat(first).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(retry).isSameAs(first);
        assertThat(boundary.grantCalls()).isOne();

        var noWorkRuntime = TaskCTestFixture.runtime("n2-terminal");
        var terminalBoundary = new TaskCTestFixture.RecordingGrantBoundary();
        var terminalMatcher = new CentralWorkMatcher(terminalBoundary);
        RequestWorkResult terminal = terminalMatcher.match(
                noWorkRuntime.requestWork(), noWorkRuntime.context(), List.of());
        RequestWorkResult terminalRetry = terminalMatcher.match(
                noWorkRuntime.requestWork(), noWorkRuntime.context(), List.of(work.candidate()));

        assertThat(terminal).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(terminalRetry).isSameAs(terminal);
        assertThat(terminalBoundary.grantCalls()).isZero();
    }

    @Test
    void n3TwoWorkersRacingSameTaskReceiveAtMostOneAtomicGrant() throws Exception {
        var workerA = TaskCTestFixture.runtime("n3-a");
        var workerB = TaskCTestFixture.runtime("n3-b");
        var work = TaskCTestFixture.candidate(3);
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<RequestWorkResult> resultA = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary).match(
                        workerA.requestWork(), workerA.context(), List.of(work.candidate()));
            });
            Future<RequestWorkResult> resultB = executor.submit(() -> {
                start.await();
                return new CentralWorkMatcher(boundary).match(
                        workerB.requestWork(), workerB.context(), List.of(work.candidate()));
            });
            start.countDown();

            assertThat(List.of(resultA.get(), resultB.get()))
                    .filteredOn(RequestWorkResult::granted)
                    .hasSize(1);
            assertThat(boundary.grantCalls()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void n4GrantSeamRequiresAssignmentReservationLeaseAttemptAndGenerationAtomically() {
        assertThat(AtomicAssignmentGrantBoundary.ATOMIC_AUTHORITIES).containsExactlyInAnyOrder(
                AtomicAssignmentGrantBoundary.GrantAuthority.EXECUTION_ASSIGNMENT,
                AtomicAssignmentGrantBoundary.GrantAuthority.RESERVATION,
                AtomicAssignmentGrantBoundary.GrantAuthority.TASK_LEASE,
                AtomicAssignmentGrantBoundary.GrantAuthority.EXECUTION_ATTEMPT,
                AtomicAssignmentGrantBoundary.GrantAuthority.EXECUTION_OWNERSHIP_GENERATION);
    }

    @Test
    void n5GrantSeamFailureLeavesNothingAuthoritative() {
        assertThat(AtomicAssignmentGrantBoundary.FAILURE_DISPOSITION)
                .isEqualTo(AtomicAssignmentGrantBoundary.GrantFailureDisposition.NONE_AUTHORITATIVE);
    }

    @Test
    void n6TaskWithActiveNativeLeaseIsNotMatched() {
        var runtime = TaskCTestFixture.runtime("n6");
        var work = TaskCTestFixture.candidate(
                6,
                PendingNativeWorkCandidate.ClaimState.ACTIVE_NATIVE_LEASE,
                ReservationFeasibility.FEASIBLE,
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER));
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(), runtime.context(), List.of(work.candidate()));

        assertThat(result).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(boundary.grantCalls()).isZero();
    }

    @Test
    void n7WorkerCannotReplaceExactProviderBinding() {
        List<Class<?>> requestTypes = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
        assertThat(requestTypes).doesNotContain(ProviderBindingPin.class);

        var work = TaskCTestFixture.candidate(7);
        var foreignProvider = TaskBTestFixture.provider("foreign-n7");
        assertThatIllegalArgumentException().isThrownBy(() -> new PendingNativeWorkCandidate(
                        work.graph(),
                        work.task(),
                        foreignProvider,
                        work.candidate().providerHardwareRequirement(),
                        work.candidate().runtimeDependencyRequirements(),
                        ProviderBackendExecutionSupport.declared(
                                work.task().providerBindingPin(),
                                Set.of(ExecutionBackend.NATIVE_PULL_WORKER)),
                        PendingNativeWorkCandidate.ClaimState.PENDING,
                        work.candidate().resourceDemand(),
                        ReservationFeasibility.FEASIBLE,
                        SandboxRuntimeRequirement.NOT_REQUIRED,
                        Optional.empty(),
                        ProviderProbeRequirement.NOT_REQUIRED,
                        Optional.empty()))
                .withMessageContaining("cannot rebind");
    }

    @Test
    void n8WorkerCannotReplaceMatcherSelectedNativeBackend() {
        List<Class<?>> requestTypes = Arrays.stream(RequestWork.class.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
        assertThat(requestTypes).doesNotContain(ExecutionBackend.class);
        var runtime = TaskCTestFixture.runtime("n8");
        var work = TaskCTestFixture.candidate(
                8,
                PendingNativeWorkCandidate.ClaimState.PENDING,
                ReservationFeasibility.FEASIBLE,
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER, ExecutionBackend.OPEN_CUE_FARM));
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(), runtime.context(), List.of(work.candidate()));

        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(boundary.lastCommand().backendSelection().backend())
                .isEqualTo(ExecutionBackend.NATIVE_PULL_WORKER);
    }

    @Test
    void n9StaleHostSnapshotPreventsMatchingAndRequiresReprobe() {
        var runtime = TaskCTestFixture.staleRuntime("n9");
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(),
                runtime.context(),
                List.of(TaskCTestFixture.candidate(9).candidate()));

        assertThat(result).isEqualTo(new RequestWorkResult.ReprobeRequired(
                runtime.requestWork().requestWorkId(),
                RequestWorkFailureReason.STALE_HOST_RESOURCE_SNAPSHOT));
        assertThat(boundary.grantCalls()).isZero();
    }

    @Test
    void n10RuntimeIncarnationMismatchPreventsMatching() {
        var runtime = TaskCTestFixture.runtimeWithIncarnationMismatch("n10");
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(),
                runtime.context(),
                List.of(TaskCTestFixture.candidate(10).candidate()));

        assertThat(result).isEqualTo(new RequestWorkResult.Rejected(
                runtime.requestWork().requestWorkId(),
                RequestWorkFailureReason.RUNTIME_INCARNATION_MISMATCH));
        assertThat(boundary.grantCalls()).isZero();
    }

    @Test
    void n11AuthoritativeReservationConflictPreventsMatching() {
        var runtime = TaskCTestFixture.runtime("n11");
        var work = TaskCTestFixture.candidate(
                11,
                PendingNativeWorkCandidate.ClaimState.PENDING,
                ReservationFeasibility.CONFLICT,
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER));
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(), runtime.context(), List.of(work.candidate()));

        assertThat(result).isInstanceOf(RequestWorkResult.NoWork.class);
        assertThat(boundary.grantCalls()).isZero();
    }

    @Test
    void n12SelectionIsDeterministicUnderPendingInputPermutation() {
        var runtime = TaskCTestFixture.runtime("n12");
        var first = TaskCTestFixture.candidate(1);
        var second = TaskCTestFixture.candidate(2);
        var third = TaskCTestFixture.candidate(3);

        ExecutableTaskId selectedA = selectedTask(
                runtime,
                List.of(third.candidate(), first.candidate(), second.candidate()),
                "request-n12-a");
        ExecutableTaskId selectedB = selectedTask(
                runtime,
                List.of(second.candidate(), third.candidate(), first.candidate()),
                "request-n12-b");

        assertThat(selectedA).isEqualTo(first.task().id());
        assertThat(selectedB).isEqualTo(first.task().id());
    }

    @Test
    void n13WorkerReadyEvidenceNeverChoosesWhichGlobalTaskItReceives() {
        var runtime = TaskCTestFixture.runtime("n13");
        var higherIdentity = TaskCTestFixture.candidate(13);
        var lowerIdentity = TaskCTestFixture.candidate(12);
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();

        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                runtime.requestWork(),
                runtime.context(),
                List.of(higherIdentity.candidate(), lowerIdentity.candidate()));

        assertThat(runtime.requestWork().workerRuntimeAvailability().isReachable()).isTrue();
        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        assertThat(boundary.lastCommand().executableTask().id())
                .isEqualTo(lowerIdentity.task().id());
    }

    private static ExecutableTaskId selectedTask(
            TaskCTestFixture.RuntimeFixture runtime,
            List<PendingNativeWorkCandidate> candidates,
            String requestId) {
        var boundary = new TaskCTestFixture.RecordingGrantBoundary();
        RequestWork request = runtime.requestWithId(requestId);
        RequestWorkResult result = new CentralWorkMatcher(boundary).match(
                request, runtime.context(), candidates);
        assertThat(result).isInstanceOf(RequestWorkResult.Granted.class);
        return boundary.lastCommand().executableTask().id();
    }
}
