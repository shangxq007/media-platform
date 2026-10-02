package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.ProviderCompositionDeclaration;
import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.execution.taskgraph.BoundaryAction;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.providerplugin.PendingNativeWorkAdmission;
import com.example.platform.providerplugin.PendingNativeWorkProjection;
import com.example.platform.providerplugin.PendingNativeWorkProjectionException;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.AssignmentGrantReference;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantCommand;
import com.example.platform.workerfabric.domain.AvailabilityState;
import com.example.platform.workerfabric.domain.CapacitySnapshot;
import com.example.platform.workerfabric.domain.CpuCapacity;
import com.example.platform.workerfabric.domain.HostLocation;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.HostResourceSnapshotFreshnessPolicy;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.HostResourceSnapshotSchemaVersion;
import com.example.platform.workerfabric.domain.LocalWorkerRuntimeIncarnationBinding;
import com.example.platform.workerfabric.domain.MemoryCapacity;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.domain.ObservedCpuUsage;
import com.example.platform.workerfabric.domain.ObservedMemoryUsage;
import com.example.platform.workerfabric.domain.ObservedTemporaryStorageUsage;
import com.example.platform.workerfabric.domain.ObservedUsage;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.PhysicalHostAvailability;
import com.example.platform.workerfabric.domain.PhysicalHostDescriptor;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkFailureReason;
import com.example.platform.workerfabric.domain.RequestWorkId;
import com.example.platform.workerfabric.domain.RequestWorkResult;
import com.example.platform.workerfabric.domain.RequestWorkValidationContext;
import com.example.platform.workerfabric.domain.RuntimeEnvironmentAvailability;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.SchedulableCapacity;
import com.example.platform.workerfabric.domain.SchedulableCapacityDisposition;
import com.example.platform.workerfabric.domain.TemporaryStorageCapacity;
import com.example.platform.workerfabric.domain.TrustZoneId;
import com.example.platform.workerfabric.domain.WorkerRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerRuntimeDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * P2-5b-2a-2a-3c: {@link PendingNativeWorkAdmission} projects the graph's pending work and admits it
 * through the canonical Native Pull port, returning the matcher's result unchanged and propagating
 * every failure.
 *
 * <p>Exercised against the real FFmpeg contribution, the real canonical bound graph and a real
 * {@link NativePullAdmissionPort} over a test-only in-memory grant boundary (no database).
 */
class PendingNativeWorkAdmissionTest {

    private static final String SOURCE_DIGEST = "22".repeat(32);
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void passesExactlyTheProjectedCandidatesToTheAdmissionPort() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        RuntimeFixture runtime = runtimeFixture();
        RequestWorkResult canned = new RequestWorkResult.NoWork(runtime.requestWork().requestWorkId());
        NativePullAdmissionPort port = mock(NativePullAdmissionPort.class);
        when(port.admit(any(), any(), any())).thenReturn(canned);

        RequestWorkResult result = PendingNativeWorkAdmission.admit(
                port, graph, contribution, runtime.requestWork(), runtime.validationContext());

        assertThat(result).isSameAs(canned);
        List<PendingNativeWorkCandidate> supplied = capturedCandidates(port);
        assertThat(supplied)
                .isEqualTo(PendingNativeWorkProjection.projectAll(graph, contribution));
        assertThat(supplied).hasSize(graph.tasks().size());
    }

    @Test
    void admitsEveryTaskOfAMultiTaskGraph() {
        ProviderBoundExecutableTaskGraph graph = twoUnitGraph();
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        RuntimeFixture runtime = runtimeFixture();
        NativePullAdmissionPort port = mock(NativePullAdmissionPort.class);
        when(port.admit(any(), any(), any())).thenReturn(
                new RequestWorkResult.NoWork(runtime.requestWork().requestWorkId()));

        PendingNativeWorkAdmission.admit(
                port, graph, contribution, runtime.requestWork(), runtime.validationContext());

        assertThat(capturedCandidates(port))
                .isEqualTo(PendingNativeWorkProjection.projectAll(graph, contribution))
                .hasSize(2);
    }

    @Test
    void drivesTheRealAdmissionPortAndReturnsItsResultUnchanged() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        RuntimeFixture runtime = runtimeFixture();
        NativePullAdmissionPort port = new NativePullAdmissionPort(new InMemoryGrantBoundary());

        RequestWorkResult result = PendingNativeWorkAdmission.admit(
                port, graph, contribution, runtime.requestWork(), runtime.validationContext());

        assertThat(result.requestWorkId()).isEqualTo(runtime.requestWork().requestWorkId());
        // The helper is exactly "projection + admit": the port's own call returns the same result.
        assertThat(port.admit(
                        runtime.requestWork(),
                        runtime.validationContext(),
                        PendingNativeWorkProjection.projectAll(graph, contribution)))
                .isEqualTo(result);
        // Deterministic for identical inputs.
        assertThat(PendingNativeWorkAdmission.admit(
                        port, graph, contribution, runtime.requestWork(), runtime.validationContext()))
                .isEqualTo(result);
    }

    @Test
    void propagatesProjectionFailureWithoutTouchingThePort() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        RuntimeFixture runtime = runtimeFixture();
        NativePullAdmissionPort port = mock(NativePullAdmissionPort.class);
        ProviderPluginContribution undeclaredHardware =
                withoutHardwareRequirement();

        assertThatThrownBy(() -> PendingNativeWorkAdmission.admit(
                port, graph, undeclaredHardware, runtime.requestWork(), runtime.validationContext()))
                .isInstanceOf(PendingNativeWorkProjectionException.class);
        verifyNoInteractions(port);
    }

    @Test
    void rejectsNullInputs() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();
        RuntimeFixture runtime = runtimeFixture();
        NativePullAdmissionPort port = new NativePullAdmissionPort(new InMemoryGrantBoundary());

        assertThatThrownBy(() -> PendingNativeWorkAdmission.admit(
                null, graph, contribution, runtime.requestWork(), runtime.validationContext()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingNativeWorkAdmission.admit(
                port, null, contribution, runtime.requestWork(), runtime.validationContext()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingNativeWorkAdmission.admit(
                port, graph, contribution, null, runtime.validationContext()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingNativeWorkAdmission.admit(
                port, graph, contribution, runtime.requestWork(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    private static List<PendingNativeWorkCandidate> capturedCandidates(NativePullAdmissionPort port) {
        ArgumentCaptor<Collection<PendingNativeWorkCandidate>> captor =
                ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(port).admit(any(), any(), captor.capture());
        return List.copyOf(captor.getValue());
    }

    // ---------- TEST-ONLY in-memory grant boundary (no database) ----------

    /** TEST-ONLY contribution double: the real FFmpeg declarations minus the hardware requirement. */
    private static ProviderPluginContribution withoutHardwareRequirement() {
        FfmpegProviderPluginContribution delegate = new FfmpegProviderPluginContribution();
        return new ProviderPluginContribution() {
            @Override
            public Optional<com.example.platform.workerfabric.domain.ProviderHardwareRequirement>
                    providerHardwareRequirement() {
                return Optional.empty();
            }
            @Override public String pluginId() { return delegate.pluginId(); }
            @Override public String pluginVersion() { return delegate.pluginVersion(); }
            @Override public com.example.platform.extension.domain.PluginDescriptor
                    pluginDescriptor() { return delegate.pluginDescriptor(); }
            @Override public com.example.platform.execution.domain.provider.ProviderDescriptor
                    providerDescriptor() { return delegate.providerDescriptor(); }
            @Override public com.example.platform.execution.domain.provider.ProviderExecutionContract
                    providerExecutionContract() { return delegate.providerExecutionContract(); }
            @Override public com.example.platform.execution.domain.provider.ProviderCapabilityProfile
                    providerCapabilityProfile() { return delegate.providerCapabilityProfile(); }
            @Override public com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement
                    workerRuntimeSupportRequirement() {
                return delegate.workerRuntimeSupportRequirement();
            }
            @Override public com.example.platform.execution.domain.provider.ProviderBindingPin
                    providerBindingPin() { return delegate.providerBindingPin(); }
            @Override public com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding<?>
                    createRuntimeBinding(
                            com.example.platform.providerplugin.ProviderPluginRuntimeContext context) {
                return delegate.createRuntimeBinding(context);
            }
        };
    }

    private static final class InMemoryGrantBoundary implements AtomicAssignmentGrantBoundary {

        private final Map<RequestWorkId, RequestWorkResult> resolutions = new LinkedHashMap<>();

        @Override
        public Optional<RequestWorkResult> findResolution(RequestWork requestWork) {
            return Optional.ofNullable(resolutions.get(requestWork.requestWorkId()));
        }

        @Override
        public Optional<RequestWorkFailureReason> validateRegistration(RequestWork requestWork) {
            return Optional.empty();
        }

        @Override
        public Optional<com.example.platform.workerfabric.domain.AssignmentGrant> findCurrentGrant(
                ExecutableTaskId taskId) {
            return Optional.empty();
        }

        @Override
        public RequestWorkResult resolveTerminal(
                RequestWork requestWork, RequestWorkResult terminalResult) {
            resolutions.putIfAbsent(requestWork.requestWorkId(), terminalResult);
            return resolutions.get(requestWork.requestWorkId());
        }

        @Override
        public RequestWorkResult tryGrant(AtomicAssignmentGrantCommand command) {
            RequestWorkId requestWorkId = command.requestWork().requestWorkId();
            RequestWorkResult prior = resolutions.get(requestWorkId);
            if (prior != null) {
                return prior;
            }
            RequestWorkResult granted = new RequestWorkResult.Granted(
                    requestWorkId,
                    new TestGrant(requestWorkId, command.executableTask().id()));
            resolutions.put(requestWorkId, granted);
            return granted;
        }
    }

    private record TestGrant(RequestWorkId requestWorkId, ExecutableTaskId executableTaskId)
            implements AssignmentGrantReference {
    }

    // ---------- TEST-ONLY local runtime fixture ----------

    private record RuntimeFixture(
            RequestWork requestWork, RequestWorkValidationContext validationContext) {
    }

    private static RuntimeFixture runtimeFixture() {
        PhysicalHostId hostId = PhysicalHostId.of("host-3c");
        PhysicalHostIncarnationId hostIncarnation = PhysicalHostIncarnationId.of("host-inc-3c");
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-3c");
        WorkerRuntimeIncarnationId runtimeIncarnation = WorkerRuntimeIncarnationId.of("runtime-inc-3c");
        CapacitySnapshot capacity = new CapacitySnapshot(
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000_000),
                TemporaryStorageCapacity.ofBytes(100_000_000),
                Map.of());
        HostResourceSnapshot snapshot = new HostResourceSnapshot(
                hostId,
                hostIncarnation,
                HostResourceSnapshotGeneration.first(),
                NOW,
                HostResourceSnapshotSchemaVersion.CURRENT,
                capacity,
                new ObservedUsage(
                        new ObservedCpuUsage(0.1),
                        new ObservedMemoryUsage(1_000),
                        new ObservedTemporaryStorageUsage(1_000),
                        Map.of()),
                Optional.empty());
        SchedulableCapacity schedulableCapacity = new SchedulableCapacity(
                hostId,
                hostIncarnation,
                SchedulableCapacityDisposition.AVAILABLE,
                capacity.cpu(),
                capacity.memory(),
                capacity.temporaryStorage(),
                Map.of());
        WorkerRuntimeAvailability runtimeAvailability =
                new WorkerRuntimeAvailability(runtimeId, runtimeIncarnation, AvailabilityState.REACHABLE);
        RequestWork requestWork = new RequestWork(
                RequestWorkId.of("request-3c"),
                runtimeId,
                runtimeIncarnation,
                hostId,
                hostIncarnation,
                snapshot,
                runtimeAvailability,
                Map.of(),
                RuntimeEnvironmentAvailability.UNKNOWN,
                SandboxRuntimeAvailability.UNKNOWN,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        RequestWorkValidationContext validationContext = new RequestWorkValidationContext(
                WorkerRuntimeDescriptor.local(
                        runtimeId, RuntimeLifecycleKind.EPHEMERAL_TASK, hostId),
                runtimeAvailability,
                new LocalWorkerRuntimeIncarnationBinding(
                        runtimeId, runtimeIncarnation, hostId, hostIncarnation),
                new PhysicalHostDescriptor(
                        hostId, HostLocation.of("region-a"), TrustZoneId.of("trusted"), List.of()),
                new PhysicalHostAvailability(hostId, hostIncarnation, AvailabilityState.REACHABLE),
                snapshot,
                new HostResourceSnapshotFreshnessPolicy(
                        Duration.ofMinutes(5), HostResourceSnapshotSchemaVersion.CURRENT),
                NOW,
                schedulableCapacity);
        return new RuntimeFixture(requestWork, validationContext);
    }

    // ---------- TEST-ONLY two-unit bound graph ----------

    private static ProviderBoundExecutableTaskGraph twoUnitGraph() {
        PhysicalPlanUnit first = unit("ffmpeg-transcode-a", "input-media-a", "output-media-a");
        PhysicalPlanUnit second = unit("ffmpeg-transcode-b", "input-media-b", "output-media-b");
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("phase3c-two-unit-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("phase3c-two-unit-fingerprint"),
                List.of(first, second),
                null,
                new PhysicalExecutionPlanDigest("phase3c-two-unit-plan-digest"));
        ProviderCandidate provider = new ProviderCandidate(
                FfmpegCpuProvider.BINDING,
                FfmpegCpuProvider.DESCRIPTOR,
                FfmpegCpuProvider.EXECUTION_CONTRACT,
                FfmpegCpuProvider.CAPABILITY_PROFILE,
                FfmpegCpuProvider.STATIC_COMPATIBILITY);
        ProviderFeasibilityView feasibilityView = ProviderFeasibilityView.build(
                plan,
                List.of(CompatibilityRequest.forUnit(first), CompatibilityRequest.forUnit(second)),
                List.of(provider),
                List.of());
        return ProviderBoundExecutableTaskGraph.derive(
                plan,
                feasibilityView,
                List.of(taskFor(feasibilityView, provider, first), taskFor(feasibilityView, provider, second)),
                List.of());
    }

    private static ExecutableTask taskFor(
            ProviderFeasibilityView feasibilityView, ProviderCandidate provider, PhysicalPlanUnit unit) {
        List<ExecutableTaskMembership> membership =
                ExecutableTaskMembership.canonicalForUnits(List.of(unit));
        var composition = ProviderLocalCompositionEvaluator.evaluate(ProviderLocalCompositionRequest.of(
                membership,
                feasibilityView,
                provider,
                new ProviderCompositionDeclaration(
                        FfmpegCpuProvider.BINDING,
                        ProviderCompositionDeclaration.NativePipelineSupport.SUPPORTED),
                List.of()));
        OutputDeclaration output = unit.typedOutputs().getFirst();
        return ExecutableTask.create(
                composition,
                List.of(new BoundaryAction(
                        BoundaryAction.Phase.POST_EXECUTION,
                        0,
                        new BoundaryAction.IntermediateArtifactTarget(
                                unit.stepId(),
                                output,
                                output.intermediateArtifactExpectations().getFirst()))));
    }

    private static PhysicalPlanUnit unit(String stepId, String inputId, String outputId) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(stepId),
                "logical-" + stepId,
                new RenderNodeId("render-" + stepId),
                new RenderNodeKind.Decode(),
                "transcode",
                List.of(new InputBinding(
                        new ExecutionInputId(inputId),
                        "logical-" + stepId,
                        new ExecutionStepId(stepId),
                        new RenderNodeId("render-" + stepId),
                        null, null, null, null,
                        new SourceArtifact(
                                new ArtifactId("source-media"), ContentDigest.sha256(SOURCE_DIGEST)),
                        null)),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId(outputId),
                        "logical-" + stepId,
                        new RenderNodeId("render-" + stepId),
                        List.of(), List.of(),
                        List.of(new IntermediateArtifactExpectation(
                                new LogicalArtifactId("logical-artifact-" + outputId),
                                RenderOutputRole.RENDER_MASTER)),
                        List.of())),
                List.of(),
                null, null,
                List.of(), List.of(),
                null,
                true);
    }
}
