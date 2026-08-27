package com.example.platform.workerfabric.domain;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.Objects;
import java.util.Optional;

/** Central projection of one provider-bound task considered for Native Pull placement. */
public record PendingNativeWorkCandidate(
        ProviderBoundExecutableTaskGraph providerBoundGraph,
        ExecutableTask executableTask,
        ProviderCandidate staticallyCompatibleProviderCandidate,
        ProviderBackendExecutionSupport backendExecutionSupport,
        ClaimState claimState,
        RuntimeResourceDemand resourceDemand,
        ReservationFeasibility authoritativeReservationFeasibility,
        SandboxRuntimeRequirement sandboxRequirement,
        Optional<WorkerRuntimeSupportRequirement> runtimeSupportRequirement,
        ProviderProbeRequirement providerProbeRequirement,
        Optional<ProviderProbeResult> providerProbeResult) {

    public PendingNativeWorkCandidate {
        Objects.requireNonNull(providerBoundGraph, "providerBoundGraph");
        Objects.requireNonNull(executableTask, "executableTask");
        Objects.requireNonNull(staticallyCompatibleProviderCandidate,
                "staticallyCompatibleProviderCandidate");
        Objects.requireNonNull(backendExecutionSupport, "backendExecutionSupport");
        Objects.requireNonNull(claimState, "claimState");
        Objects.requireNonNull(resourceDemand, "resourceDemand");
        Objects.requireNonNull(authoritativeReservationFeasibility,
                "authoritativeReservationFeasibility");
        Objects.requireNonNull(sandboxRequirement, "sandboxRequirement");
        runtimeSupportRequirement = Objects.requireNonNull(
                runtimeSupportRequirement, "runtimeSupportRequirement");
        Objects.requireNonNull(providerProbeRequirement, "providerProbeRequirement");
        providerProbeResult = Objects.requireNonNull(providerProbeResult,
                "providerProbeResult");

        if (providerBoundGraph.tasks().stream().noneMatch(candidate -> candidate == executableTask)) {
            throw new IllegalArgumentException(
                    "Native Pull candidate must be the exact task from its provider-bound ETG");
        }
        if (!executableTask.providerBindingPin().equals(
                staticallyCompatibleProviderCandidate.bindingPin())
                || !executableTask.providerBindingPin().equals(
                        backendExecutionSupport.providerBindingPin())) {
            throw new IllegalArgumentException(
                    "Native Pull candidate cannot rebind the task ProviderBindingPin");
        }
        providerProbeResult.ifPresent(probe -> {
            if (!executableTask.providerBindingPin().equals(probe.providerBindingPin())) {
                throw new IllegalArgumentException(
                        "Native Pull provider probe cannot rebind the task ProviderBindingPin");
            }
        });
        runtimeSupportRequirement.ifPresent(requirement -> {
            if (!executableTask.providerBindingPin().equals(requirement.providerBindingPin())) {
                throw new IllegalArgumentException(
                        "Native Pull runtime support requirement cannot rebind the task ProviderBindingPin");
            }
        });
    }

    public boolean pendingWithoutActiveLease() {
        return claimState == ClaimState.PENDING;
    }

    public enum ClaimState {
        PENDING,
        ACTIVE_NATIVE_LEASE,
        NOT_PENDING
    }
}
