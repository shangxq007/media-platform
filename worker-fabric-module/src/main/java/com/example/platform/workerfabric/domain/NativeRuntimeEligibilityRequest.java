package com.example.platform.workerfabric.domain;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable Stage-2 evidence for one Native Pull runtime candidate.
 *
 * <p>Construction proves the exact task/provider candidate was admitted by the Stage-1 graph and
 * that Native Pull was already selected. OpenCue and remote-provider evaluation intentionally use
 * no nullable fake host/worker context.
 */
public record NativeRuntimeEligibilityRequest(
        ProviderBoundExecutableTaskGraph providerBoundGraph,
        ExecutableTask executableTask,
        ProviderCandidate staticallyCompatibleProviderCandidate,
        ExecutionBackendSelection backendSelection,
        Optional<WorkerRuntimeDescriptor> workerRuntime,
        Optional<WorkerRuntimeAvailability> workerAvailability,
        Optional<LocalWorkerRuntimeIncarnationBinding> runtimeHostBinding,
        Optional<PhysicalHostDescriptor> physicalHost,
        Optional<PhysicalHostAvailability> hostAvailability,
        Optional<HostResourceSnapshot> hostResourceSnapshot,
        HostResourceSnapshotFreshnessPolicy snapshotFreshnessPolicy,
        Instant evaluatedAt,
        Optional<DeviceDescriptor> device,
        Optional<DeviceAvailability> deviceAvailability,
        Optional<SchedulableCapacity> schedulableCapacity,
        RuntimeResourceDemand resourceDemand,
        ReservationFeasibility reservationFeasibility,
        RuntimeEnvironmentAvailability runtimeEnvironmentAvailability,
        SandboxRuntimeRequirement sandboxRequirement,
        SandboxRuntimeAvailability sandboxRuntimeAvailability,
        Optional<WorkerRuntimeSupportAdvertisement> runtimeSupportAdvertisement,
        Optional<WorkerRuntimeSupportRequirement> runtimeSupportRequirement,
        ProviderProbeRequirement providerProbeRequirement,
        Optional<ProviderProbeResult> providerProbeResult) {

    public NativeRuntimeEligibilityRequest {
        Objects.requireNonNull(providerBoundGraph, "providerBoundGraph");
        Objects.requireNonNull(executableTask, "executableTask");
        Objects.requireNonNull(staticallyCompatibleProviderCandidate,
                "staticallyCompatibleProviderCandidate");
        Objects.requireNonNull(backendSelection, "backendSelection");
        workerRuntime = Objects.requireNonNull(workerRuntime, "workerRuntime");
        workerAvailability = Objects.requireNonNull(workerAvailability, "workerAvailability");
        runtimeHostBinding = Objects.requireNonNull(runtimeHostBinding, "runtimeHostBinding");
        physicalHost = Objects.requireNonNull(physicalHost, "physicalHost");
        hostAvailability = Objects.requireNonNull(hostAvailability, "hostAvailability");
        hostResourceSnapshot = Objects.requireNonNull(hostResourceSnapshot, "hostResourceSnapshot");
        Objects.requireNonNull(snapshotFreshnessPolicy, "snapshotFreshnessPolicy");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        device = Objects.requireNonNull(device, "device");
        deviceAvailability = Objects.requireNonNull(deviceAvailability, "deviceAvailability");
        schedulableCapacity = Objects.requireNonNull(schedulableCapacity, "schedulableCapacity");
        Objects.requireNonNull(resourceDemand, "resourceDemand");
        Objects.requireNonNull(reservationFeasibility, "reservationFeasibility");
        Objects.requireNonNull(runtimeEnvironmentAvailability, "runtimeEnvironmentAvailability");
        Objects.requireNonNull(sandboxRequirement, "sandboxRequirement");
        Objects.requireNonNull(sandboxRuntimeAvailability, "sandboxRuntimeAvailability");
        runtimeSupportAdvertisement = Objects.requireNonNull(
                runtimeSupportAdvertisement, "runtimeSupportAdvertisement");
        runtimeSupportRequirement = Objects.requireNonNull(
                runtimeSupportRequirement, "runtimeSupportRequirement");
        Objects.requireNonNull(providerProbeRequirement, "providerProbeRequirement");
        providerProbeResult = Objects.requireNonNull(providerProbeResult, "providerProbeResult");

        boolean exactGraphTask = providerBoundGraph.tasks().stream()
                .anyMatch(candidate -> candidate == executableTask);
        if (!exactGraphTask) {
            throw new IllegalArgumentException(
                    "runtime eligibility requires an exact task from the provider-bound ETG");
        }
        if (!backendSelection.selectsExactTask(executableTask)
                || backendSelection.backend() != ExecutionBackend.NATIVE_PULL_WORKER) {
            throw new IllegalArgumentException(
                    "native runtime eligibility requires the exact selected Native Pull task");
        }
        if (!executableTask.providerBindingPin().equals(
                staticallyCompatibleProviderCandidate.bindingPin())) {
            throw new IllegalArgumentException(
                    "runtime eligibility candidate cannot rebind the task ProviderBindingPin");
        }
        executableTask.memberships().forEach(membership ->
                providerBoundGraph.providerCompatibilityGraph().requireStaticallyFeasible(
                        membership.physicalPlanUnit(), staticallyCompatibleProviderCandidate));
        providerProbeResult.ifPresent(probe -> {
            if (!probe.providerBindingPin().equals(executableTask.providerBindingPin())) {
                throw new IllegalArgumentException(
                        "provider probe must bind the task ProviderBindingPin");
            }
        });
        runtimeSupportRequirement.ifPresent(requirement -> {
            if (!requirement.providerBindingPin().equals(executableTask.providerBindingPin())) {
                throw new IllegalArgumentException(
                        "runtime support requirement cannot rebind the task ProviderBindingPin");
            }
        });
    }
}
