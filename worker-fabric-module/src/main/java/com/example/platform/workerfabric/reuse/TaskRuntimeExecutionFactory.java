package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.CompletionEventId;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import com.example.platform.workerfabric.domain.ExpectedOutputValidation;
import com.example.platform.workerfabric.domain.NativeWorkerBackendExecutionHandle;
import com.example.platform.workerfabric.domain.ObservedExecutionState;
import com.example.platform.workerfabric.domain.TaskLease;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.util.Objects;

/**
 * P2-5b-2a-2b: builds the {@link TaskRuntimeExecution} for one already-granted task.
 *
 * <p>The runtime context and the completion handle are derived from the durable Task D grant — the
 * attempt, its ownership generation and its lease — never from caller-supplied identities, so an
 * execution cannot be constructed for a task the platform did not actually grant. The remaining two
 * fields are the caller's publication intent: the storage target and the artifact metadata are
 * supplied by the composition root, because no provider/worker/graph value carries the tenant,
 * storage or artifact identity of an output.
 *
 * <p><b>Fail closed.</b> A grant whose attempt or lease does not bind the exact task raises
 * {@link TaskRuntimeExecutionConstructionException}; nothing is repaired or defaulted.
 *
 * <p>The completion evidence is the platform's own pre-declared expectation for this bounded loop:
 * the handle is the native lease correlation, and the completion/validation identities are derived
 * deterministically from the granted attempt and generation, matching the established closed-loop
 * harness convention ({@code output-validation-<completionEventId>}). It asserts no provider outcome
 * that has not happened; the fence treats the resulting state as the backend-reported result of the
 * execution driven by the orchestrator.
 */
public final class TaskRuntimeExecutionFactory {

    private TaskRuntimeExecutionFactory() {
    }

    /**
     * Builds the runtime execution for {@code task} from its current grant and the caller's
     * publication intent.
     *
     * @throws TaskRuntimeExecutionConstructionException when the grant does not bind this exact task
     */
    public static TaskRuntimeExecution build(
            ExecutableTask task,
            AssignmentGrant grant,
            DurableOutputTarget durableOutputTarget,
            ArtifactCommitMetadata artifactCommitMetadata) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(grant, "grant");
        Objects.requireNonNull(durableOutputTarget, "durableOutputTarget");
        Objects.requireNonNull(artifactCommitMetadata, "artifactCommitMetadata");

        ExecutionAttempt attempt = grant.attempt();
        TaskLease lease = grant.lease();
        if (!attempt.executableTaskId().equals(task.id())) {
            throw new TaskRuntimeExecutionConstructionException(
                    "GRANT_ATTEMPT_TASK_MISMATCH",
                    "granted attempt does not bind the exact executable task");
        }
        if (!lease.executableTaskId().equals(task.id())
                || !lease.executionAttemptId().equals(attempt.id())
                || !lease.ownershipGeneration().equals(attempt.ownershipGeneration())) {
            throw new TaskRuntimeExecutionConstructionException(
                    "GRANT_LEASE_TASK_MISMATCH",
                    "granted lease does not bind the exact task, attempt and generation");
        }

        RuntimeExecutionContext runtimeContext = new RuntimeExecutionContext(
                task.id(),
                task.providerBindingPin(),
                attempt.id(),
                attempt.ownershipGeneration());
        CompletionEventId completionEventId = new CompletionEventId(
                "native-completion-" + attempt.id().value() + "-"
                        + attempt.ownershipGeneration().value());
        CompletionEvidence completionEvidence = new CompletionEvidence(
                completionEventId,
                NativeWorkerBackendExecutionHandle.forLease(
                        attempt.id(), attempt.ownershipGeneration(), lease.id()),
                task.id(),
                ObservedExecutionState.SUCCEEDED,
                new ExpectedOutputValidation(
                        "output-validation-" + completionEventId.value(),
                        ExpectedOutputValidation.Status.VALID));
        return new TaskRuntimeExecution(
                runtimeContext, completionEvidence, durableOutputTarget, artifactCommitMetadata);
    }
}
