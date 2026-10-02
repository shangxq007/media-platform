package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
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
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.providerplugin.PendingNativeWorkProjection;
import com.example.platform.providerplugin.PendingNativeWorkProjectionException;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.ProviderBackendExecutionSupport;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.ProviderProbeRequirement;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import com.example.platform.workerfabric.domain.ReservationFeasibility;
import com.example.platform.workerfabric.domain.RuntimeResourceDemand;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2a-2a-3b: {@link PendingNativeWorkProjection} builds the 13-field
 * {@link PendingNativeWorkCandidate} from the bound graph and the provider's own declarations, and
 * fails closed when either authority is missing a value.
 *
 * <p>Exercised against the real FFmpeg contribution and the real canonical bound-graph fixture. A
 * catalog is never supplied, which is the point: the candidate travels with the graph.
 */
class PendingNativeWorkProjectionTest {

    private static final String SOURCE_DIGEST = "11".repeat(32);

    @Test
    void projectsAllThirteenFieldsFromTheGraphAndTheContribution() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        ExecutableTask task = graph.tasks().getFirst();
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();

        PendingNativeWorkCandidate candidate =
                PendingNativeWorkProjection.project(task, graph, contribution);

        assertThat(candidate.providerBoundGraph()).isSameAs(graph);
        assertThat(candidate.executableTask()).isSameAs(task);
        assertThat(candidate.staticallyCompatibleProviderCandidate().bindingPin())
                .isEqualTo(FfmpegCpuProvider.BINDING);
        assertThat(candidate.providerHardwareRequirement())
                .isEqualTo(contribution.providerHardwareRequirement().orElseThrow());
        assertThat(candidate.runtimeDependencyRequirements()).isEmpty();
        assertThat(candidate.backendExecutionSupport())
                .isEqualTo(contribution.providerBackendExecutionSupport().orElseThrow());
        assertThat(candidate.claimState())
                .isEqualTo(PendingNativeWorkCandidate.ClaimState.PENDING);
        assertThat(candidate.resourceDemand())
                .isEqualTo(new RuntimeResourceDemand(2000L, 1073741824L, 2147483648L, Map.of()));
        assertThat(candidate.authoritativeReservationFeasibility())
                .isEqualTo(ReservationFeasibility.UNKNOWN);
        assertThat(candidate.sandboxRequirement()).isEqualTo(SandboxRuntimeRequirement.REQUIRED);
        assertThat(candidate.runtimeSupportRequirement())
                .contains(FfmpegCpuProvider.RUNTIME_SUPPORT_REQUIREMENT);
        assertThat(candidate.providerProbeRequirement())
                .isEqualTo(ProviderProbeRequirement.NOT_REQUIRED);
        assertThat(candidate.providerProbeResult()).isEmpty();
    }

    @Test
    void fieldThreeComesFromTheBoundGraphsOwnFeasibilityViewNotACatalog() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        ExecutableTask task = graph.tasks().getFirst();

        PendingNativeWorkCandidate candidate = PendingNativeWorkProjection.project(
                task, graph, new FfmpegProviderPluginContribution());

        ProviderCandidate fromGraph = graph.providerFeasibilityView().unitCandidates().stream()
                .flatMap(node -> node.compatibilityProofs().stream())
                .map(StaticProviderCompatibilityProof::providerCandidate)
                .filter(provider -> provider.bindingPin().equals(FfmpegCpuProvider.BINDING))
                .findFirst()
                .orElseThrow();
        assertThat(candidate.staticallyCompatibleProviderCandidate()).isEqualTo(fromGraph);
        assertThat(candidate.staticallyCompatibleProviderCandidate().descriptor())
                .isEqualTo(FfmpegCpuProvider.DESCRIPTOR);
        assertThat(candidate.backendExecutionSupport().supportedBackends())
                .containsExactly(ExecutionBackend.NATIVE_PULL_WORKER);
    }

    @Test
    void projectsEveryTaskIndependentlyAndDeterministically() {
        ProviderBoundExecutableTaskGraph graph = twoUnitGraph();
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();

        List<PendingNativeWorkCandidate> first =
                PendingNativeWorkProjection.projectAll(graph, contribution);
        List<PendingNativeWorkCandidate> second =
                PendingNativeWorkProjection.projectAll(graph, contribution);

        assertThat(graph.tasks()).hasSize(2);
        assertThat(first).hasSize(graph.tasks().size());
        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(PendingNativeWorkCandidate::executableTask)
                .containsExactlyElementsOf(graph.tasks());
        assertThat(first).extracting(PendingNativeWorkCandidate::providerBoundGraph)
                .containsOnly(graph);
        assertThat(first).extracting(PendingNativeWorkCandidate::claimState)
                .containsOnly(PendingNativeWorkCandidate.ClaimState.PENDING);
    }

    @Test
    void failsClosedWhenAProviderDeclarationIsMissing() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        ExecutableTask task = graph.tasks().getFirst();

        assertThatThrownBy(() -> PendingNativeWorkProjection.project(
                task, graph, OverridingContribution.withoutHardwareRequirement()))
                .isInstanceOf(PendingNativeWorkProjectionException.class)
                .hasMessageContaining("PROVIDER_HARDWARE_REQUIREMENT_UNDECLARED");
        assertThatThrownBy(() -> PendingNativeWorkProjection.project(
                task, graph, OverridingContribution.withoutBackendExecutionSupport()))
                .isInstanceOf(PendingNativeWorkProjectionException.class)
                .hasMessageContaining("PROVIDER_BACKEND_EXECUTION_SUPPORT_UNDECLARED");
        assertThatThrownBy(() -> PendingNativeWorkProjection.project(
                task, graph, OverridingContribution.withoutResourceProfile()))
                .isInstanceOf(PendingNativeWorkProjectionException.class)
                .hasMessageContaining("PROVIDER_RESOURCE_PROFILE_UNDECLARED");
    }

    @Test
    void rejectsNullInputs() {
        ProviderBoundExecutableTaskGraph graph = FfmpegCanonicalGraphFixture.single(SOURCE_DIGEST);
        ExecutableTask task = graph.tasks().getFirst();
        FfmpegProviderPluginContribution contribution = new FfmpegProviderPluginContribution();

        assertThatThrownBy(() -> PendingNativeWorkProjection.project(null, graph, contribution))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingNativeWorkProjection.project(task, null, contribution))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingNativeWorkProjection.project(task, graph, null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Two independent single-unit memberships: two tasks, no cross-task dependency. */
    private static ProviderBoundExecutableTaskGraph twoUnitGraph() {
        PhysicalPlanUnit first = unit("ffmpeg-transcode-a", "input-media-a", "output-media-a");
        PhysicalPlanUnit second = unit("ffmpeg-transcode-b", "input-media-b", "output-media-b");
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("phase3b-two-unit-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("phase3b-two-unit-fingerprint"),
                List.of(first, second),
                null,
                new PhysicalExecutionPlanDigest("phase3b-two-unit-plan-digest"));
        ProviderCandidate provider = providerCandidate();
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

    private static ProviderCandidate providerCandidate() {
        return new ProviderCandidate(
                FfmpegCpuProvider.BINDING,
                FfmpegCpuProvider.DESCRIPTOR,
                FfmpegCpuProvider.EXECUTION_CONTRACT,
                FfmpegCpuProvider.CAPABILITY_PROFILE,
                FfmpegCpuProvider.STATIC_COMPATIBILITY);
    }

    /** TEST-ONLY contribution double: real FFmpeg declarations with one dropped on demand. */
    private static final class OverridingContribution implements ProviderPluginContribution {

        private final ProviderPluginContribution delegate = new FfmpegProviderPluginContribution();
        private final boolean hardware;
        private final boolean backend;
        private final boolean profile;

        private OverridingContribution(boolean hardware, boolean backend, boolean profile) {
            this.hardware = hardware;
            this.backend = backend;
            this.profile = profile;
        }

        static ProviderPluginContribution withoutHardwareRequirement() {
            return new OverridingContribution(false, true, true);
        }

        static ProviderPluginContribution withoutBackendExecutionSupport() {
            return new OverridingContribution(true, false, true);
        }

        static ProviderPluginContribution withoutResourceProfile() {
            return new OverridingContribution(true, true, false);
        }

        @Override
        public Optional<ProviderHardwareRequirement> providerHardwareRequirement() {
            return hardware ? delegate.providerHardwareRequirement() : Optional.empty();
        }

        @Override
        public Optional<ProviderBackendExecutionSupport> providerBackendExecutionSupport() {
            return backend ? delegate.providerBackendExecutionSupport() : Optional.empty();
        }

        @Override
        public Optional<ProviderResourceProfile> resourceProfile() {
            return profile ? delegate.resourceProfile() : Optional.empty();
        }

        @Override public String pluginId() { return delegate.pluginId(); }
        @Override public String pluginVersion() { return delegate.pluginVersion(); }
        @Override public PluginDescriptor pluginDescriptor() { return delegate.pluginDescriptor(); }
        @Override public com.example.platform.execution.domain.provider.ProviderDescriptor
                providerDescriptor() { return delegate.providerDescriptor(); }
        @Override public com.example.platform.execution.domain.provider.ProviderExecutionContract
                providerExecutionContract() { return delegate.providerExecutionContract(); }
        @Override public com.example.platform.execution.domain.provider.ProviderCapabilityProfile
                providerCapabilityProfile() { return delegate.providerCapabilityProfile(); }
        @Override public WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement() {
            return delegate.workerRuntimeSupportRequirement();
        }
        @Override public com.example.platform.execution.domain.provider.ProviderBindingPin
                providerBindingPin() { return delegate.providerBindingPin(); }
        @Override public ProviderNativeRuntimeBinding<?> createRuntimeBinding(
                ProviderPluginRuntimeContext context) {
            return delegate.createRuntimeBinding(context);
        }
    }
}
