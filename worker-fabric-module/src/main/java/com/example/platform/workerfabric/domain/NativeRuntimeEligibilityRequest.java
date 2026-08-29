package com.example.platform.workerfabric.domain;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable Stage-2 evidence for one Native Pull runtime candidate.
 *
 * <p>Construction proves the exact task/provider candidate was admitted by the Stage-1 feasibility
 * view and
 * that Native Pull was already selected. OpenCue and remote-provider evaluation intentionally use
 * no nullable fake host/worker context.
 */
public record NativeRuntimeEligibilityRequest(
        ProviderBoundExecutableTaskGraph providerBoundGraph,
        ExecutableTask executableTask,
        ProviderCandidate staticallyCompatibleProviderCandidate,
        List<StaticProviderCompatibilityProof> staticCompatibilityProofs,
        ProviderHardwareRequirement providerHardwareRequirement,
        List<RuntimeDependencyRequirement> runtimeDependencyRequirements,
        Optional<ProviderHardwareObservation> providerHardwareObservation,
        Optional<RuntimeDependencyObservation> runtimeDependencyObservation,
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
        staticCompatibilityProofs = List.copyOf(Objects.requireNonNull(
                staticCompatibilityProofs, "staticCompatibilityProofs"));
        Objects.requireNonNull(providerHardwareRequirement, "providerHardwareRequirement");
        runtimeDependencyRequirements = List.copyOf(Objects.requireNonNull(
                runtimeDependencyRequirements, "runtimeDependencyRequirements"));
        providerHardwareObservation = Objects.requireNonNull(
                providerHardwareObservation, "providerHardwareObservation");
        runtimeDependencyObservation = Objects.requireNonNull(
                runtimeDependencyObservation, "runtimeDependencyObservation");
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
        if (executableTask.memberships().isEmpty()
                || staticCompatibilityProofs.size() != executableTask.memberships().size()) {
            throw new IllegalArgumentException(
                    "runtime eligibility requires one exact Stage-1 proof per task membership");
        }
        for (int index = 0; index < executableTask.memberships().size(); index++) {
            var membership = executableTask.memberships().get(index);
            StaticProviderCompatibilityProof proof = staticCompatibilityProofs.get(index);
            try {
                providerBoundGraph.requireExactStaticCompatibilityProof(
                        membership.physicalPlanUnitId(),
                        staticallyCompatibleProviderCandidate,
                        proof);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "runtime eligibility Stage-1 proof does not bind the exact task membership and provider",
                        exception);
            }
        }
        var expectedProviderImplementationId = staticallyCompatibleProviderCandidate
                .descriptor().providerImplementationId();
        if (!providerHardwareRequirement.providerImplementationId()
                .equals(expectedProviderImplementationId)) {
            throw new IllegalArgumentException(
                    "runtime eligibility hardware requirement cannot rebind ProviderImplementationId");
        }
        for (RuntimeDependencyRequirement requirement : runtimeDependencyRequirements) {
            Objects.requireNonNull(requirement, "runtimeDependencyRequirements element");
            if (!requirement.providerImplementationId().equals(expectedProviderImplementationId)) {
                throw new IllegalArgumentException(
                        "runtime eligibility dependency requirement cannot rebind ProviderImplementationId");
            }
        }
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
