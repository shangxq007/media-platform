package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** C1-C6 completion-fence adversarial matrix. */
class CompletionFenceTest {

    private TaskFTestFixture.RecordingArtifactCommitEvidencePort artifacts;
    private TaskFTestFixture.RecordingCompletionAuthorityPort authority;
    private CompletionFence fence;

    @BeforeEach
    void setUp() {
        artifacts = new TaskFTestFixture.RecordingArtifactCommitEvidencePort();
        authority = new TaskFTestFixture.RecordingCompletionAuthorityPort();
        fence = new CompletionFence(artifacts, authority);
    }

    @Test
    void c1BackendSucceededWithoutArtifactCommitDoesNotCompleteTask() {
        CompletionEvidence evidence = validCompletion("c1", TaskFTestFixture.remoteHandle());

        assertThat(fence.tryComplete(evidence))
                .isEqualTo(CompletionDecision.ARTIFACT_NOT_COMMITTED_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void c2ArtifactBytesWithoutAuthoritativeCommitDoNotCompleteTask() {
        CompletionEvidence evidence = validCompletion("c2", TaskFTestFixture.remoteHandle());
        artifacts.recordBytesExist(evidence.completionEventId());

        assertThat(artifacts.bytesExist(evidence.completionEventId())).isTrue();
        assertThat(fence.tryComplete(evidence))
                .isEqualTo(CompletionDecision.ARTIFACT_NOT_COMMITTED_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void c3ArtifactCommittedForStaleGenerationDoesNotCompleteTask() {
        BackendExecutionHandle staleHandle = TaskFTestFixture.remoteHandle(
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION,
                "remote-stale-generation");
        authority = new TaskFTestFixture.RecordingCompletionAuthorityPort(
                TaskFTestFixture.TASK_ID,
                TaskFTestFixture.ATTEMPT_ID,
                TaskFTestFixture.GENERATION.next());
        fence = new CompletionFence(artifacts, authority);
        CompletionEvidence evidence = validCompletion("c3", staleHandle);
        artifacts.recordAuthoritativeCommit(evidence.completionEventId());

        assertThat(fence.tryComplete(evidence))
                .isEqualTo(CompletionDecision.STALE_GENERATION_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void c4CurrentAttemptGenerationTaskOutputAndCommitPermitCompletion() {
        CompletionEvidence evidence = validCompletion("c4", TaskFTestFixture.remoteHandle());
        artifacts.recordAuthoritativeCommit(evidence.completionEventId());

        assertThat(fence.tryComplete(evidence)).isEqualTo(CompletionDecision.COMPLETED);
        assertThat(authority.completionCount()).isOne();
    }

    @Test
    void c5DuplicateCompletionEventIsIdempotent() {
        CompletionEvidence evidence = validCompletion("c5", TaskFTestFixture.remoteHandle());
        artifacts.recordAuthoritativeCommit(evidence.completionEventId());

        assertThat(fence.tryComplete(evidence)).isEqualTo(CompletionDecision.COMPLETED);
        assertThat(fence.tryComplete(evidence)).isEqualTo(CompletionDecision.DUPLICATE_NOOP);
        assertThat(authority.completionCount()).isOne();
    }

    @Test
    void c6LateCompletionFromAbandonedAttemptIsRejected() {
        ExecutionAttemptId abandonedAttempt = new ExecutionAttemptId("attempt-abandoned");
        BackendExecutionHandle lateHandle = TaskFTestFixture.remoteHandle(
                abandonedAttempt, TaskFTestFixture.GENERATION, "remote-abandoned");
        CompletionEvidence evidence = validCompletion("c6", lateHandle);
        artifacts.recordAuthoritativeCommit(evidence.completionEventId());

        assertThat(fence.tryComplete(evidence))
                .isEqualTo(CompletionDecision.STALE_ATTEMPT_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    @Test
    void expectedTaskMismatchAndInvalidOutputFailClosed() {
        CompletionEvidence wrongTask = TaskFTestFixture.completion(
                "task-mismatch",
                TaskFTestFixture.remoteHandle(),
                new ExecutableTaskId("b".repeat(64)),
                ExpectedOutputValidation.Status.VALID);
        artifacts.recordAuthoritativeCommit(wrongTask.completionEventId());
        assertThat(fence.tryComplete(wrongTask))
                .isEqualTo(CompletionDecision.EXPECTED_TASK_MISMATCH_REJECTED);

        CompletionEvidence invalidOutput = TaskFTestFixture.completion(
                "invalid-output",
                TaskFTestFixture.remoteHandle(),
                TaskFTestFixture.TASK_ID,
                ExpectedOutputValidation.Status.INVALID);
        artifacts.recordAuthoritativeCommit(invalidOutput.completionEventId());
        assertThat(fence.tryComplete(invalidOutput))
                .isEqualTo(CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED);
        assertThat(authority.completionCount()).isZero();
    }

    private static CompletionEvidence validCompletion(
            String completionId, BackendExecutionHandle handle) {
        return TaskFTestFixture.completion(
                completionId,
                handle,
                TaskFTestFixture.TASK_ID,
                ExpectedOutputValidation.Status.VALID);
    }
}
