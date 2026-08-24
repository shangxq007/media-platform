package com.example.platform.workerfabric.domain;

import static org.mockito.Mockito.mock;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory contract doubles for Task F; no production integration or Artifact identity exists. */
final class TaskFTestFixture {

    static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    static final ExecutionAttemptId ATTEMPT_ID = new ExecutionAttemptId("attempt-current");
    static final ExecutionOwnershipGeneration GENERATION = ExecutionOwnershipGeneration.first();
    static final ProviderBindingPin BINDING = mock(ProviderBindingPin.class, "task-f-binding");

    private TaskFTestFixture() {}

    static RemoteProviderExecutionHandle remoteHandle() {
        return remoteHandle(ATTEMPT_ID, GENERATION, "remote-current");
    }

    static RemoteProviderExecutionHandle remoteHandle(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation,
            String remoteId) {
        return RemoteProviderExecutionHandle.forRemoteExecution(
                attemptId, generation, new RemoteExecutionId(remoteId));
    }

    static ExecutionObservation observation(
            String observationId,
            ObservedExecutionState state,
            BackendExecutionHandle handle) {
        return new ExecutionObservation(
                new ObservationId(observationId),
                handle.executionAttemptId(),
                handle.ownershipGeneration(),
                handle,
                BINDING,
                state,
                NOW,
                Optional.empty());
    }

    static CompletionEvidence completion(
            String completionId,
            BackendExecutionHandle handle,
            ExecutableTaskId expectedTaskId,
            ExpectedOutputValidation.Status outputStatus) {
        return new CompletionEvidence(
                new CompletionEventId(completionId),
                handle,
                expectedTaskId,
                ObservedExecutionState.SUCCEEDED,
                new ExpectedOutputValidation("output-validation-" + completionId, outputStatus));
    }

    static final class RecordingArtifactCommitEvidencePort
            implements ArtifactCommitEvidencePort {

        private final Map<CompletionEventId, ArtifactCommitEvidence> committed = new HashMap<>();
        private final Set<CompletionEventId> bytesExistWithoutCommit = new HashSet<>();

        void recordBytesExist(CompletionEventId completionEventId) {
            bytesExistWithoutCommit.add(completionEventId);
        }

        void recordAuthoritativeCommit(CompletionEventId completionEventId) {
            committed.put(
                    completionEventId,
                    new ArtifactCommitEvidence(
                            "artifact-authority-evidence-" + completionEventId.value(), NOW));
        }

        boolean bytesExist(CompletionEventId completionEventId) {
            return bytesExistWithoutCommit.contains(completionEventId);
        }

        @Override
        public Optional<ArtifactCommitEvidence> committedEvidenceFor(
                CompletionEvidence completionEvidence) {
            return Optional.ofNullable(committed.get(completionEvidence.completionEventId()));
        }
    }

    static final class RecordingCompletionAuthorityPort implements CompletionAuthorityPort {

        private final ExecutableTaskId currentTaskId;
        private final ExecutionAttemptId currentAttemptId;
        private final ExecutionOwnershipGeneration currentGeneration;
        private final Set<CompletionEventId> completedEvents = new HashSet<>();
        private int completionCount;

        RecordingCompletionAuthorityPort() {
            this(TASK_ID, ATTEMPT_ID, GENERATION);
        }

        RecordingCompletionAuthorityPort(
                ExecutableTaskId currentTaskId,
                ExecutionAttemptId currentAttemptId,
                ExecutionOwnershipGeneration currentGeneration) {
            this.currentTaskId = currentTaskId;
            this.currentAttemptId = currentAttemptId;
            this.currentGeneration = currentGeneration;
        }

        @Override
        public synchronized CompletionDecision completeIfCurrent(
                CompletionEvidence completionEvidence,
                ArtifactCommitEvidence artifactCommitEvidence) {
            if (completedEvents.contains(completionEvidence.completionEventId())) {
                return CompletionDecision.DUPLICATE_NOOP;
            }
            if (!completionEvidence.expectedExecutableTaskId().equals(currentTaskId)) {
                return CompletionDecision.EXPECTED_TASK_MISMATCH_REJECTED;
            }
            BackendExecutionHandle handle = completionEvidence.backendExecutionHandle();
            if (!handle.executionAttemptId().equals(currentAttemptId)) {
                return CompletionDecision.STALE_ATTEMPT_REJECTED;
            }
            if (!handle.ownershipGeneration().equals(currentGeneration)) {
                return CompletionDecision.STALE_GENERATION_REJECTED;
            }
            if (!completionEvidence.expectedOutputValidation().isValid()
                    || artifactCommitEvidence == null) {
                throw new IllegalStateException("completion preconditions bypassed");
            }
            completedEvents.add(completionEvidence.completionEventId());
            completionCount++;
            return CompletionDecision.COMPLETED;
        }

        int completionCount() {
            return completionCount;
        }
    }

    static final class RecordingObservationIngestionPort
            implements ExecutionObservationIngestionPort {

        private final ExecutionOwnershipGeneration currentGeneration;
        private final Map<ObservationId, ExecutionObservation> observations = new HashMap<>();

        RecordingObservationIngestionPort(ExecutionOwnershipGeneration currentGeneration) {
            this.currentGeneration = currentGeneration;
        }

        @Override
        public synchronized IngestionResult ingest(ExecutionObservation observation) {
            if (observations.putIfAbsent(observation.observationId(), observation) != null) {
                return IngestionResult.DUPLICATE_NOOP;
            }
            return observation.ownershipGeneration().equals(currentGeneration)
                    ? IngestionResult.RECORDED_CURRENT_EVIDENCE
                    : IngestionResult.RECORDED_STALE_EVIDENCE;
        }

        Optional<ExecutionObservation> find(ObservationId observationId) {
            return Optional.ofNullable(observations.get(observationId));
        }
    }

    static final class RecordingOpenCueSubmissionLedger
            implements OpenCueSubmissionLedgerPort {

        private final Map<ExecutionAttemptId, OpenCueBackendExecutionHandle> submissions =
                new HashMap<>();

        @Override
        public synchronized RegistrationResult register(OpenCueBackendExecutionHandle handle) {
            OpenCueBackendExecutionHandle prior = submissions.putIfAbsent(
                    handle.executionAttemptId(), handle);
            if (prior == null) {
                return RegistrationResult.RECORDED;
            }
            return prior.equals(handle)
                    ? RegistrationResult.DUPLICATE_SAME_SUBMISSION_NOOP
                    : RegistrationResult.CONFLICTING_SECOND_SUBMISSION_REJECTED;
        }

        @Override
        public synchronized Optional<OpenCueBackendExecutionHandle> findByAttempt(
                ExecutionAttemptId executionAttemptId) {
            return Optional.ofNullable(submissions.get(executionAttemptId));
        }
    }
}
