package com.example.platform.workerfabric.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.compatibility.CompatibilityKernel;
import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Deterministic exact Stage-1 and I2/I3 evidence shared by P20-I4 integration tests. */
final class I4TestFixture {

    private static final RuntimeDependencyCoordinate NATIVE_EXECUTABLE =
            RuntimeDependencyCoordinate.of("native.executable");

    private I4TestFixture() {}

    static StageOneScenario stageOneScenario(
            String providerName, String unitName, ExecutableTaskId taskId) {
        ProviderCandidate provider = provider(providerName);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class, unitName + "-unit");
        when(unit.stepId()).thenReturn(new ExecutionStepId(unitName));
        when(unit.logicalNodeId()).thenReturn(unitName);
        when(unit.typedInputs()).thenReturn(List.of());
        when(unit.typedOutputs()).thenReturn(List.of());
        when(unit.capabilityRequirementRefs()).thenReturn(List.of());
        when(unit.executionIntentRefs()).thenReturn(List.of());

        CompatibilityRequest compatibilityRequest = CompatibilityRequest.forUnit(unit);
        StaticProviderCompatibilityProof proof = CompatibilityKernel
                .evaluate(compatibilityRequest, provider)
                .staticCompatibilityProof()
                .orElseThrow();
        ProviderFeasibilityView feasibilityView =
                mock(ProviderFeasibilityView.class, unitName + "-feasibility-view");
        when(feasibilityView.requireStaticallyFeasible(unit, provider)).thenReturn(proof);

        ExecutableTaskMembership membership =
                mock(ExecutableTaskMembership.class, unitName + "-membership");
        when(membership.physicalPlanUnit()).thenReturn(unit);
        ExecutableTask task = mock(ExecutableTask.class, unitName + "-task");
        when(task.id()).thenReturn(taskId);
        when(task.providerBindingPin()).thenReturn(provider.bindingPin());
        when(task.memberships()).thenReturn(List.of(membership));

        ProviderBoundExecutableTaskGraph graph =
                mock(ProviderBoundExecutableTaskGraph.class, unitName + "-task-graph");
        when(graph.tasks()).thenReturn(List.of(task));
        when(graph.digest()).thenReturn(new ExecutableTaskGraphDigest("2".repeat(64)));
        when(graph.providerFeasibilityView()).thenReturn(feasibilityView);
        return new StageOneScenario(graph, task, provider, List.of(proof));
    }

    static ProviderCandidate provider(String name) {
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                ProviderId.of(name),
                ProviderImplementationId.of(name + ".native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                profileReference,
                List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                binding.providerId(),
                binding.providerImplementationId(),
                binding.providerVersion(),
                binding.providerExecutionContractVersion(),
                profileReference);
        return new ProviderCandidate(
                binding,
                descriptor,
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1),
                        binding.providerExecutionContractVersion(),
                        List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }

    static ProviderHardwareRequirement hardwareRequirement(
            ProviderImplementationId providerImplementationId) {
        return new ProviderHardwareRequirement(
                providerImplementationId,
                CpuArchitecture.X86_64,
                Optional.of(new ProviderHardwareDeviceRequirement(
                        DeviceKind.GPU,
                        Optional.of(DeviceVendor.of("vendor")),
                        Optional.of(DeviceModel.of("model")),
                        new DriverRuntimeRequirement(
                                RuntimeDependencyVersionConstraint.exact(
                                        RuntimeDependencyVersion.of("12.4")),
                                Optional.of(RuntimeDependencyAbi.of("driver.12"))),
                        List.of("tensor.compute"))),
                List.of("module.gpu"),
                List.of("codec.h264"),
                List.of("device.access"));
    }

    static ProviderHardwareRequirement hostHardwareRequirement(
            ProviderImplementationId providerImplementationId) {
        return new ProviderHardwareRequirement(
                providerImplementationId,
                CpuArchitecture.X86_64,
                Optional.empty(),
                List.of("module.gpu"),
                List.of("codec.h264"),
                List.of("device.access"));
    }

    static RuntimeDependencyRequirement dependencyRequirement(
            ProviderImplementationId providerImplementationId) {
        return new RuntimeDependencyRequirement(
                providerImplementationId,
                NATIVE_EXECUTABLE,
                RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of("1.0")),
                Optional.of(RuntimeDependencyAbi.of("native.1")),
                List.of("codec.h264"),
                List.of("feature.enabled"));
    }

    static ProviderHardwareObservation hardwareObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId runtimeId,
            PhysicalHostId hostId,
            Optional<DeviceId> deviceId,
            Instant assessedAt) {
        return hardwareObservation(
                providerImplementationId,
                runtimeId,
                hostId,
                deviceId,
                assessedAt.minus(Duration.ofMinutes(1)),
                assessedAt.plus(Duration.ofMinutes(1)),
                matchingHardwareEvidence());
    }

    static ProviderHardwareObservation hardwareObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId runtimeId,
            PhysicalHostId hostId,
            Optional<DeviceId> deviceId,
            Instant observedAt,
            Instant expiresAt,
            ProviderHardwareProbeEvidence evidence) {
        return new ProviderHardwareObservation(
                providerImplementationId,
                runtimeId,
                hostId,
                deviceId,
                observedAt,
                expiresAt,
                evidence);
    }

    static ProviderHardwareAvailableEvidence matchingHardwareEvidence() {
        return new ProviderHardwareAvailableEvidence(
                CpuArchitecture.X86_64,
                List.of("module.gpu"),
                List.of("codec.h264"),
                List.of("device.access"),
                Optional.of(new ProviderHardwareAvailableDevice(
                        DeviceKind.GPU,
                        DeviceVendor.of("vendor"),
                        DeviceModel.of("model"),
                        new DriverRuntimeObservation(
                                RuntimeDependencyVersion.of("12.4"),
                                Optional.of(RuntimeDependencyAbi.of("driver.12"))),
                        List.of("tensor.compute"))));
    }

    static ProviderHardwareAvailableEvidence matchingHostHardwareEvidence() {
        return new ProviderHardwareAvailableEvidence(
                CpuArchitecture.X86_64,
                List.of("module.gpu"),
                List.of("codec.h264"),
                List.of("device.access"),
                Optional.empty());
    }

    static RuntimeDependencyObservation dependencyObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId runtimeId,
            Optional<DeviceId> deviceId,
            Instant assessedAt) {
        return dependencyObservation(
                providerImplementationId,
                runtimeId,
                deviceId,
                RuntimeDependencyProbeSchemaVersion.CURRENT,
                assessedAt.minus(Duration.ofMinutes(1)),
                assessedAt.plus(Duration.ofMinutes(1)),
                List.of(matchingObservedDependency()));
    }

    static RuntimeDependencyObservation dependencyObservation(
            ProviderImplementationId providerImplementationId,
            WorkerRuntimeId runtimeId,
            Optional<DeviceId> deviceId,
            RuntimeDependencyProbeSchemaVersion schemaVersion,
            Instant observedAt,
            Instant expiresAt,
            List<RuntimeDependencyObservedDependency> dependencies) {
        return new RuntimeDependencyObservation(
                providerImplementationId,
                runtimeId,
                deviceId,
                schemaVersion,
                observedAt,
                expiresAt,
                dependencies);
    }

    static RuntimeDependencyObservedDependency matchingObservedDependency() {
        return observedDependency(
                "1.0", Optional.of(RuntimeDependencyAbi.of("native.1")),
                List.of("codec.h264"), List.of("feature.enabled"));
    }

    static RuntimeDependencyObservedDependency observedDependency(
            String version,
            Optional<RuntimeDependencyAbi> abi,
            List<String> features,
            List<String> flags) {
        return new RuntimeDependencyObservedDependency(
                NATIVE_EXECUTABLE,
                RuntimeDependencyVersion.of(version),
                abi,
                features,
                flags);
    }

    record StageOneScenario(
            ProviderBoundExecutableTaskGraph graph,
            ExecutableTask task,
            ProviderCandidate provider,
            List<StaticProviderCompatibilityProof> proofs) {}
}
