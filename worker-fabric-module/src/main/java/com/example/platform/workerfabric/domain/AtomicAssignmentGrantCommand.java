package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTask;
import java.util.Objects;

/** Exact, eligibility-proven command passed to the Task D atomic grant transaction. */
public record AtomicAssignmentGrantCommand(
        RequestWork requestWork,
        ExecutableTask executableTask,
        ExecutionBackendSelection backendSelection,
        RuntimeEligibilityDecision runtimeEligibilityDecision,
        RuntimeResourceDemand resourceDemand,
        HostResourceSnapshot authoritativeHostResourceSnapshot,
        SchedulableCapacity authoritativeSchedulableCapacity) {

    public AtomicAssignmentGrantCommand {
        Objects.requireNonNull(requestWork, "requestWork");
        Objects.requireNonNull(executableTask, "executableTask");
        Objects.requireNonNull(backendSelection, "backendSelection");
        Objects.requireNonNull(runtimeEligibilityDecision, "runtimeEligibilityDecision");
        Objects.requireNonNull(resourceDemand, "resourceDemand");
        Objects.requireNonNull(authoritativeHostResourceSnapshot,
                "authoritativeHostResourceSnapshot");
        Objects.requireNonNull(authoritativeSchedulableCapacity,
                "authoritativeSchedulableCapacity");

        if (!backendSelection.selectsExactTask(executableTask)
                || backendSelection.backend() != ExecutionBackend.NATIVE_PULL_WORKER) {
            throw new IllegalArgumentException(
                    "atomic Native Pull grant requires the matcher's exact backend selection");
        }
        if (!runtimeEligibilityDecision.eligible()
                || !runtimeEligibilityDecision.executableTaskId().equals(executableTask.id())
                || !runtimeEligibilityDecision.providerBindingPin().equals(
                        executableTask.providerBindingPin())) {
            throw new IllegalArgumentException(
                    "atomic grant requires exact RuntimeEligibility proof");
        }
        if (!requestWork.hostResourceSnapshot().equals(authoritativeHostResourceSnapshot)) {
            throw new IllegalArgumentException(
                    "atomic grant must use the exact validated host resource snapshot");
        }
        if (!authoritativeSchedulableCapacity.physicalHostId().equals(
                        requestWork.physicalHostId())
                || !authoritativeSchedulableCapacity.physicalHostIncarnationId().equals(
                        requestWork.physicalHostIncarnationId())) {
            throw new IllegalArgumentException(
                    "atomic grant capacity must bind the exact RequestWork host incarnation");
        }
    }
}
