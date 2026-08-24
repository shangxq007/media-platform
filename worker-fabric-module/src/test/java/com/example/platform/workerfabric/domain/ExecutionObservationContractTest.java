package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** O1-O8 normalized-observation and delivery adversarial matrix. */
class ExecutionObservationContractTest {

    @Test
    void o1SameObservationIdTwiceIsIdempotent() {
        TaskFTestFixture.RecordingObservationIngestionPort ingestion =
                new TaskFTestFixture.RecordingObservationIngestionPort(
                        TaskFTestFixture.GENERATION);
        ExecutionObservation observation = TaskFTestFixture.observation(
                "o1", ObservedExecutionState.RUNNING, TaskFTestFixture.remoteHandle());

        assertThat(ingestion.ingest(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult
                        .RECORDED_CURRENT_EVIDENCE);
        assertThat(ingestion.ingest(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult.DUPLICATE_NOOP);
    }

    @Test
    void o2CurrentGenerationRunningMayUpdateRuntimeEvidence() {
        TaskFTestFixture.RecordingObservationIngestionPort ingestion =
                new TaskFTestFixture.RecordingObservationIngestionPort(
                        TaskFTestFixture.GENERATION);
        ExecutionObservation observation = TaskFTestFixture.observation(
                "o2", ObservedExecutionState.RUNNING, TaskFTestFixture.remoteHandle());

        assertThat(ingestion.ingest(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult
                        .RECORDED_CURRENT_EVIDENCE);
        assertThat(ingestion.find(observation.observationId()))
                .map(ExecutionObservation::observedExecutionState)
                .contains(ObservedExecutionState.RUNNING);
    }

    @Test
    void o3OldGenerationSucceededObservationCannotCompleteCurrentTask() {
        ExecutionOwnershipGeneration currentGeneration = TaskFTestFixture.GENERATION.next();
        TaskFTestFixture.RecordingObservationIngestionPort ingestion =
                new TaskFTestFixture.RecordingObservationIngestionPort(currentGeneration);
        BackendExecutionHandle oldHandle = TaskFTestFixture.remoteHandle(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                "remote-old-generation");
        ExecutionObservation oldSuccess = TaskFTestFixture.observation(
                "o3", ObservedExecutionState.SUCCEEDED, oldHandle);

        assertThat(ingestion.ingest(oldSuccess))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult
                        .RECORDED_STALE_EVIDENCE);

        CompletionEvidence completion = TaskFTestFixture.completion(
                "o3-completion",
                oldHandle,
                TaskFTestFixture.TASK_ID,
                ExpectedOutputValidation.Status.VALID);
        TaskFTestFixture.RecordingArtifactCommitEvidencePort artifacts =
                new TaskFTestFixture.RecordingArtifactCommitEvidencePort();
        artifacts.recordAuthoritativeCommit(completion.completionEventId());
        TaskFTestFixture.RecordingCompletionAuthorityPort authority =
                new TaskFTestFixture.RecordingCompletionAuthorityPort(
                        TaskFTestFixture.TASK_ID,
                        TaskFTestFixture.ATTEMPT_ID,
                        currentGeneration);

        assertThat(new CompletionFence(artifacts, authority).tryComplete(completion))
                .isEqualTo(CompletionDecision.STALE_GENERATION_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void o4CallbackAndPollingNormalizeToSameCanonicalShape() {
        ExecutionObservation canonical = TaskFTestFixture.observation(
                "o4", ObservedExecutionState.RUNNING, TaskFTestFixture.remoteHandle());
        WebhookIngressNormalizationPort<String> callback = ignored -> canonical;
        RemotePollingObserverPort<String> polling = ignored -> List.of(canonical);

        assertThat(callback.normalize("callback-envelope"))
                .isEqualTo(polling.poll("poll-request").getFirst());
        assertThat(ExecutionObservation.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "observationId",
                        "executionAttemptId",
                        "ownershipGeneration",
                        "backendExecutionHandle",
                        "providerBindingPin",
                        "observedExecutionState",
                        "observedAt",
                        "diagnosticReference");
    }

    @Test
    void o5PollingPolicyIsNotPartOfCapabilityContract() {
        assertThat(Arrays.stream(RemotePollingObserverPort.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .noneMatch(type -> type.getSimpleName().contains("Capability"));
        assertThat(RemotePollingObserverPort.class.getDeclaredFields()).isEmpty();
    }

    @Test
    void o6ExecutionObservationIsEvidenceNotAuthoritativeState() {
        assertThat(Arrays.stream(ExecutionObservation.class.getRecordComponents())
                .map(RecordComponent::getType))
                .doesNotContain(ExecutionAttemptState.class, CompletionDecision.class);
        assertThat(Arrays.stream(ExecutionObservation.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .noneMatch(name -> name.matches("(?i).*(complete|transition|mutate|update|save).*"));
    }

    @Test
    void o7QueueDeliveryDuplicationCannotDuplicatePlatformCompletion() {
        TaskFTestFixture.RecordingObservationIngestionPort ingestion =
                new TaskFTestFixture.RecordingObservationIngestionPort(
                        TaskFTestFixture.GENERATION);
        TaskFTestFixture.RecordingArtifactCommitEvidencePort artifacts =
                new TaskFTestFixture.RecordingArtifactCommitEvidencePort();
        TaskFTestFixture.RecordingCompletionAuthorityPort authority =
                new TaskFTestFixture.RecordingCompletionAuthorityPort();
        CompletionFence fence = new CompletionFence(artifacts, authority);
        ExecutionObservation observation = TaskFTestFixture.observation(
                "o7", ObservedExecutionState.SUCCEEDED, TaskFTestFixture.remoteHandle());
        CompletionEvidence completion = TaskFTestFixture.completion(
                "o7-completion",
                observation.backendExecutionHandle(),
                TaskFTestFixture.TASK_ID,
                ExpectedOutputValidation.Status.VALID);
        artifacts.recordAuthoritativeCommit(completion.completionEventId());
        List<CompletionDecision> completionDecisions = new ArrayList<>();
        IdempotentObservationConsumerPort consumer = delivered -> {
            ExecutionObservationIngestionPort.IngestionResult result = ingestion.ingest(delivered);
            if (result != ExecutionObservationIngestionPort.IngestionResult.DUPLICATE_NOOP) {
                completionDecisions.add(fence.tryComplete(completion));
            }
            return result;
        };

        assertThat(consumer.consume(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult
                        .RECORDED_CURRENT_EVIDENCE);
        assertThat(consumer.consume(observation))
                .isEqualTo(ExecutionObservationIngestionPort.IngestionResult.DUPLICATE_NOOP);
        assertThat(completionDecisions).containsExactly(CompletionDecision.COMPLETED);
        assertThat(authority.completionCount()).isOne();
    }

    @Test
    void o8ExternalObserverContractsHaveNoDirectCanonicalMutationAuthority() {
        assertThat(WebhookIngressNormalizationPort.class.getDeclaredMethods())
                .allMatch(method -> method.getReturnType() == ExecutionObservation.class);
        assertThat(RemotePollingObserverPort.class.getDeclaredMethods())
                .allMatch(method -> method.getReturnType() == List.class);
        assertThat(ObserverPlanePort.class.getDeclaredMethods())
                .allMatch(method -> method.getReturnType() == ExecutionObservation.class);
        assertThat(Arrays.stream(ExecutionObservationIngestionPort.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactly("ingest");
    }

    @Test
    void observationRejectsAttemptOrGenerationMismatchWithHandle() {
        BackendExecutionHandle handle = TaskFTestFixture.remoteHandle();
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExecutionObservation(
                        new ObservationId("mismatched-attempt"),
                        new ExecutionAttemptId("other-attempt"),
                        handle.ownershipGeneration(),
                        handle,
                        TaskFTestFixture.BINDING,
                        ObservedExecutionState.RUNNING,
                        TaskFTestFixture.NOW,
                        Optional.empty()));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExecutionObservation(
                        new ObservationId("mismatched-generation"),
                        handle.executionAttemptId(),
                        handle.ownershipGeneration().next(),
                        handle,
                        TaskFTestFixture.BINDING,
                        ObservedExecutionState.RUNNING,
                        TaskFTestFixture.NOW,
                        Optional.empty()));
    }
}
