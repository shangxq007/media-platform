package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Backend handle, OpenCue, RemoteProvider, and interaction-mode contract guards. */
class BackendIntegrationContractTest {

    @Test
    void backendHandlesBindAttemptGenerationBackendButNotTaskIdentity() {
        BackendExecutionHandle nativeHandle = NativeWorkerBackendExecutionHandle.forLease(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                new LeaseId("lease-f"));
        BackendExecutionHandle openCueHandle = OpenCueBackendExecutionHandle.forSubmission(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                new CueJobId("cue-job-f"));
        BackendExecutionHandle remoteHandle = TaskFTestFixture.remoteHandle();

        assertThat(ListSupport.handles(nativeHandle, openCueHandle, remoteHandle))
                .allSatisfy(handle -> {
                    assertThat(handle.executionAttemptId()).isEqualTo(TaskFTestFixture.ATTEMPT_ID);
                    assertThat(handle.ownershipGeneration()).isEqualTo(TaskFTestFixture.GENERATION);
                    assertThat(handle.backend()).isNotNull();
                    assertThat(Arrays.stream(handle.getClass().getRecordComponents())
                            .map(RecordComponent::getType))
                            .doesNotContain(ExecutableTaskId.class);
                });
    }

    @Test
    void handleBackendMismatchFailsConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new OpenCueBackendExecutionHandle(
                        TaskFTestFixture.ATTEMPT_ID,
                        TaskFTestFixture.GENERATION,
                        ExecutionBackend.REMOTE_PROVIDER,
                        new CueJobId("wrong-backend")));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new RemoteProviderExecutionHandle(
                        TaskFTestFixture.ATTEMPT_ID,
                        TaskFTestFixture.GENERATION,
                        ExecutionBackend.OPEN_CUE_FARM,
                        new RemoteExecutionId("wrong-backend")));
    }

    @Test
    void executionAttemptUsesTypedHandleAndRejectsMismatchedCorrelation() {
        RemoteProviderExecutionHandle handle = TaskFTestFixture.remoteHandle();
        ExecutionAttempt attempt = new ExecutionAttempt(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.TASK_ID,
                TaskFTestFixture.GENERATION,
                ExecutionBackend.REMOTE_PROVIDER,
                ExecutionAttemptState.RUNNING,
                java.util.Optional.of(handle));

        assertThat(attempt.backendExecutionHandle()).contains(handle);
        assertThat(Arrays.stream(ExecutionAttempt.class.getRecordComponents())
                .filter(component -> component.getName().equals("backendExecutionHandle"))
                .map(RecordComponent::getGenericType)
                .map(java.lang.reflect.Type::getTypeName))
                .containsExactly("java.util.Optional<com.example.platform.workerfabric.domain."
                        + "BackendExecutionHandle>");
        assertThatIllegalArgumentException().isThrownBy(() -> new ExecutionAttempt(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.TASK_ID,
                TaskFTestFixture.GENERATION,
                ExecutionBackend.OPEN_CUE_FARM,
                ExecutionAttemptState.RUNNING,
                java.util.Optional.of(handle)));
    }

    @Test
    void openCueHasDelegatedPlacementAndTypedEligibility() {
        TaskBTestFixture.Scenario scenario = TaskBTestFixture.scenario("opencue", "unit");
        OpenCueFarmBackend backend = new OpenCueFarmBackend(
                scenario.task().providerBindingPin());
        ProviderBackendExecutionSupport support = ProviderBackendExecutionSupport.declared(
                scenario.task().providerBindingPin(), Set.of(ExecutionBackend.OPEN_CUE_FARM));
        ExecutionBackendEligibilityDecision decision =
                OpenCueBackendEligibilityContract.canonical().evaluate(scenario.task(), support);

        assertThat(backend.executionBackend()).isEqualTo(ExecutionBackend.OPEN_CUE_FARM);
        assertThat(backend.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.BACKEND_DELEGATED);
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.backend()).isEqualTo(ExecutionBackend.OPEN_CUE_FARM);
    }

    @Test
    void onePlatformAttemptMapsToOneOpenCueSubmission() {
        TaskFTestFixture.RecordingOpenCueSubmissionLedger ledger =
                new TaskFTestFixture.RecordingOpenCueSubmissionLedger();
        OpenCueBackendExecutionHandle first = OpenCueBackendExecutionHandle.forSubmission(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                new CueJobId("cue-job-one"));
        OpenCueBackendExecutionHandle conflicting = OpenCueBackendExecutionHandle.forSubmission(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                new CueJobId("cue-job-two"));

        assertThat(ledger.register(first))
                .isEqualTo(OpenCueSubmissionLedgerPort.RegistrationResult.RECORDED);
        assertThat(ledger.register(first))
                .isEqualTo(OpenCueSubmissionLedgerPort.RegistrationResult
                        .DUPLICATE_SAME_SUBMISSION_NOOP);
        assertThat(ledger.register(conflicting))
                .isEqualTo(OpenCueSubmissionLedgerPort.RegistrationResult
                        .CONFLICTING_SECOND_SUBMISSION_REJECTED);
        assertThat(ledger.findByAttempt(TaskFTestFixture.ATTEMPT_ID)).contains(first);
    }

    @Test
    void openCueCompletionMappingCannotBypassArtifactFence() {
        OpenCueCompletionMappingContract<String> mapping = ignored ->
                TaskFTestFixture.completion(
                        "open-cue-mapped-success",
                        OpenCueBackendExecutionHandle.forSubmission(
                                TaskFTestFixture.ATTEMPT_ID,
                                TaskFTestFixture.GENERATION,
                                new CueJobId("cue-mapped-success")),
                        TaskFTestFixture.TASK_ID,
                        ExpectedOutputValidation.Status.VALID);
        TaskFTestFixture.RecordingCompletionAuthorityPort authority =
                new TaskFTestFixture.RecordingCompletionAuthorityPort();
        CompletionFence fence = new CompletionFence(
                evidence -> java.util.Optional.empty(), authority);

        assertThat(fence.tryComplete(mapping.normalizeBackendSuccess("farm-success")))
                .isEqualTo(CompletionDecision.ARTIFACT_NOT_COMMITTED_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void remoteProviderHasManagedPlacementAndTypedEligibility() {
        TaskBTestFixture.Scenario scenario = TaskBTestFixture.scenario("remote", "unit");
        RemoteProviderBackend backend = new RemoteProviderBackend(
                scenario.task().providerBindingPin(),
                RemoteProviderInteractionMode.ASYNC_CALLBACK);
        ProviderBackendExecutionSupport support = ProviderBackendExecutionSupport.declared(
                scenario.task().providerBindingPin(), Set.of(ExecutionBackend.REMOTE_PROVIDER));
        ExecutionBackendEligibilityDecision decision =
                RemoteProviderBackendEligibilityContract.canonical()
                        .evaluate(scenario.task(), support);

        assertThat(backend.executionBackend()).isEqualTo(ExecutionBackend.REMOTE_PROVIDER);
        assertThat(backend.placementAuthorityScope())
                .isEqualTo(PlacementAuthorityScope.REMOTE_PROVIDER_MANAGED);
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.backend()).isEqualTo(ExecutionBackend.REMOTE_PROVIDER);
    }

    @Test
    void remoteProviderInteractionModesAreExactTypedV1WithoutHybrid() {
        assertThat(RemoteProviderInteractionMode.values()).containsExactly(
                RemoteProviderInteractionMode.SYNCHRONOUS,
                RemoteProviderInteractionMode.ASYNC_CALLBACK,
                RemoteProviderInteractionMode.ASYNC_POLL,
                RemoteProviderInteractionMode.ASYNC_STREAM);
        assertThat(Arrays.stream(RemoteProviderInteractionMode.values())
                .map(Enum::name))
                .doesNotContain("HYBRID");
    }

    @Test
    void durableDeliveryStagesAreFrozenInAuthorityOrder() {
        assertThat(DeliveryFlowStage.values()).containsExactly(
                DeliveryFlowStage.AUTHORITATIVE_DATABASE_TRANSACTION,
                DeliveryFlowStage.OUTBOX,
                DeliveryFlowStage.DISPATCHER,
                DeliveryFlowStage.DURABLE_QUEUE_OR_MESSAGE_TRANSPORT,
                DeliveryFlowStage.IDEMPOTENT_CONSUMER,
                DeliveryFlowStage.DATABASE_FENCED_TRANSITION);
    }

    private static final class ListSupport {

        private ListSupport() {}

        static java.util.List<BackendExecutionHandle> handles(
                BackendExecutionHandle... handles) {
            return java.util.List.of(handles);
        }
    }
}
