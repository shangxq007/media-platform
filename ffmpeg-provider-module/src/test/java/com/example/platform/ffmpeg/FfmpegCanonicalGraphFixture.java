package com.example.platform.ffmpeg;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
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
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;

final class FfmpegCanonicalGraphFixture {
    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("phase19-ffmpeg-direct.v1");

    private FfmpegCanonicalGraphFixture() {}

    static ProviderBoundExecutableTaskGraph single(String sourceDigest) {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("ffmpeg-transcode"),
                "logical-ffmpeg-transcode",
                new RenderNodeId("render-ffmpeg-transcode"),
                new RenderNodeKind.Decode(),
                "transcode",
                List.of(sourceInput(sourceDigest)),
                List.of(output()),
                List.of(),
                null, null,
                List.of(), List.of(),
                null,
                true);
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("phase19-ffmpeg-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("phase19-ffmpeg-fingerprint"),
                List.of(unit),
                null,
                new PhysicalExecutionPlanDigest("phase19-ffmpeg-plan-digest"));
        ProviderCandidate candidate = candidate();
        ProviderFeasibilityView feasibilityView = ProviderFeasibilityView.build(
                plan, List.of(CompatibilityRequest.forUnit(unit)), List.of(candidate), List.of());
        var membership = ExecutableTaskMembership.canonicalForUnits(List.of(unit));
        var composition = ProviderLocalCompositionEvaluator.evaluate(
                ProviderLocalCompositionRequest.of(
                        membership,
                        feasibilityView,
                        candidate,
                        new ProviderCompositionDeclaration(
                                FfmpegCpuProvider.BINDING,
                                ProviderCompositionDeclaration.NativePipelineSupport.SUPPORTED),
                        List.of()));
        OutputDeclaration output = unit.typedOutputs().getFirst();
        ExecutableTask task = ExecutableTask.create(
                composition,
                List.of(new BoundaryAction(
                        BoundaryAction.Phase.POST_EXECUTION,
                        0,
                        new BoundaryAction.IntermediateArtifactTarget(
                                unit.stepId(),
                                output,
                                output.intermediateArtifactExpectations().getFirst()))));
        return ProviderBoundExecutableTaskGraph.derive(
                plan, feasibilityView, List.of(task), List.of());
    }

    private static ProviderCandidate candidate() {
        ProviderStaticCompatibility compatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(
                        ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(
                FfmpegCpuProvider.BINDING,
                FfmpegCpuProvider.DESCRIPTOR,
                FfmpegCpuProvider.EXECUTION_CONTRACT,
                FfmpegCpuProvider.CAPABILITY_PROFILE,
                compatibility);
    }

    private static InputBinding sourceInput(String digest) {
        return new InputBinding(
                new ExecutionInputId("input-media"),
                "logical-ffmpeg-transcode",
                new ExecutionStepId("ffmpeg-transcode"),
                new RenderNodeId("render-ffmpeg-transcode"),
                null, null, null, null,
                new SourceArtifact(
                        new ArtifactId("source-media"), ContentDigest.sha256(digest)),
                null);
    }

    private static OutputDeclaration output() {
        return new OutputDeclaration(
                new ExecutionOutputId("output-media"),
                "logical-ffmpeg-transcode",
                new RenderNodeId("render-ffmpeg-transcode"),
                List.of(), List.of(),
                List.of(new IntermediateArtifactExpectation(
                        new LogicalArtifactId("logical-artifact-output-media"),
                        RenderOutputRole.RENDER_MASTER)),
                List.of());
    }
}
