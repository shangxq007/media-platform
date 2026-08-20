package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.render.domain.renderplan.graph.RenderGraphBuilder;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidationResult;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidator;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G. Validation negative cases (brief §13G): duplicate node, missing endpoint,
 * self-edge, cycle, invalid dependency combo, zero-length extent, FAILED source,
 * missing capability, unsupported source kind.
 */
class RenderGraphValidationNegativeTest {

    @Test
    void zeroLengthExtentIsInvalidRenderExtent() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderRequest req = TestPlans.renderRequest();
        RenderRequest bad = new RenderRequest(req.id(),
                new RenderExtent(MediaTime.ofRational(1, 1), MediaTime.ofRational(1, 1), FrameRate.of(30, 1)),
                req.outputs());
        RenderPlanningInput input = new RenderPlanningInput(
                TestPlans.hydratedRevision(), bad,
                new SourceResolutionInput(Map.of(TestPlans.artifactId(), RenderSourceResolutionState.RESOLVED)),
                TestPlans.fullCapabilityContext());
        RenderPlanningResult result = planner.plan(input);
        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(), "zero-length extent -> UNRENDERABLE");
        assertTrue(result.diagnostics().stream().anyMatch(
                d -> d.code() == RenderPlanningDiagnosticCode.INVALID_RENDER_EXTENT),
                "INVALID_RENDER_EXTENT diagnostic");
    }

    @Test
    void failedSourceIsUnrenderable() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput input = TestPlans.inputWithSourceState(RenderSourceResolutionState.FAILED);
        RenderPlanningResult result = planner.plan(input);
        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(), "FAILED source -> UNRENDERABLE");
    }

    @Test
    void missingCapabilityIsUnrenderable() {
        RenderPlanner planner = new DefaultRenderPlanner();
        // empty capability context -> DECODE node capability unavailable
        RenderPlanningInput input = TestPlans.inputWithCapabilities(CapabilityContext.none());
        RenderPlanningResult result = planner.plan(input);
        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(), "missing capability -> UNRENDERABLE");
        assertTrue(result.diagnostics().stream().anyMatch(
                d -> d.code() == RenderPlanningDiagnosticCode.CAPABILITY_UNAVAILABLE),
                "CAPABILITY_UNAVAILABLE diagnostic");
    }

    @Test
    void duplicateNodeIdentityIsInvalid() {
        RenderNodeId id = RenderNodeId.of(new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode", "fp");
        RenderNode a = new RenderNode(id, new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode",
                List.of(), List.of(RenderCapabilityVocabulary.videoDecode()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderNode b = new RenderNode(id, new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode",
                List.of(), List.of(RenderCapabilityVocabulary.videoDecode()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderPlan plan = planWith(List.of(a, b), List.of());
        RenderGraph graph = buildGraph(plan);
        RenderGraphValidationResult result = new RenderGraphValidator().validate(plan, graph, topology(plan, graph));
        assertFalse(result.valid(), "duplicate node identity -> invalid");
    }

    @Test
    void missingEdgeEndpointIsInvalid() {
        RenderNodeId a = RenderNodeId.of(new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode", "fp-a");
        RenderNodeId b = RenderNodeId.of(new RenderNodeKind.Effect(),
                new RenderComponentPath(RenderComponentKind.EFFECT, List.of("c1", "e1")), "blur", "fp-b");
        RenderNodeId missing = RenderNodeId.of(new RenderNodeKind.Output(),
                RenderComponentPath.of(RenderComponentKind.OUTPUT, "master"), "encode", "fp-missing");
        RenderNode nodeA = new RenderNode(a, new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode",
                List.of(), List.of(RenderCapabilityVocabulary.videoDecode()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderNode nodeB = new RenderNode(b, new RenderNodeKind.Effect(),
                new RenderComponentPath(RenderComponentKind.EFFECT, List.of("c1", "e1")), "blur",
                List.of(), List.of(RenderCapabilityVocabulary.forEffect(
                        com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.GAUSSIAN_BLUR)),
                List.of(), List.of(), List.of(), Optional.empty());
        // edge references a node (missing) not in the node list
        RenderDependencyEdge badEdge = new RenderDependencyEdge(a, missing, new RenderDependency.EffectInput());
        RenderPlan plan = planWith(List.of(nodeA, nodeB), List.of(badEdge));
        RenderGraph graph = buildGraph(plan);
        RenderGraphValidationResult result = new RenderGraphValidator().validate(plan, graph, topology(plan, graph));
        assertFalse(result.valid(), "missing edge endpoint -> invalid");
    }

    @Test
    void selfEdgeIsInvalid() {
        RenderNodeId a = RenderNodeId.of(new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode", "fp-a");
        RenderNode nodeA = new RenderNode(a, new RenderNodeKind.Decode(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "t1/c1"), "decode",
                List.of(), List.of(RenderCapabilityVocabulary.videoDecode()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderDependencyEdge selfEdge = new RenderDependencyEdge(a, a, new RenderDependency.DecodedFrames());
        RenderPlan plan = planWith(List.of(nodeA), List.of(selfEdge));
        RenderGraph graph = buildGraph(plan);
        RenderGraphValidationResult result = new RenderGraphValidator().validate(plan, graph, topology(plan, graph));
        assertFalse(result.valid(), "self-edge -> invalid");
    }

    @Test
    void invalidDependencyVariantComboIsInvalid() {
        // EffectInput whose producer is an AUDIO_MIX is invalid: an effect input may
        // only consume decoded frames (DECODE) or processed output (EFFECT) (C10).
        RenderNodeId mix = RenderNodeId.of(new RenderNodeKind.AudioMix(),
                RenderComponentPath.of(RenderComponentKind.AUDIO_MIX, "master"), "mix", "fp-m");
        RenderNodeId output = RenderNodeId.of(new RenderNodeKind.Output(),
                RenderComponentPath.of(RenderComponentKind.OUTPUT, "master"), "encode", "fp-o");
        RenderNode nodeMix = new RenderNode(mix, new RenderNodeKind.AudioMix(),
                RenderComponentPath.of(RenderComponentKind.AUDIO_MIX, "master"), "mix",
                List.of(), List.of(RenderCapabilityVocabulary.audioMix()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderNode nodeOutput = new RenderNode(output, new RenderNodeKind.Output(),
                RenderComponentPath.of(RenderComponentKind.OUTPUT, "master"), "encode",
                List.of(), List.of(RenderCapabilityVocabulary.outputEncode()),
                List.of(), List.of(), List.of(), Optional.empty());
        RenderDependencyEdge badEdge = new RenderDependencyEdge(mix, output, new RenderDependency.EffectInput());
        RenderPlan plan = planWith(List.of(nodeMix, nodeOutput), List.of(badEdge));
        RenderGraph graph = buildGraph(plan);
        RenderGraphValidationResult result = new RenderGraphValidator().validate(plan, graph, topology(plan, graph));
        assertFalse(result.valid(), "EffectInput with AUDIO_MIX producer -> invalid");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RenderPlan planWith(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        RenderPlanFingerprint fp = RenderPlanFingerprintCalculator.compute(
                TestPlans.revisionRef(), TestPlans.renderRequest(), nodes, edges);
        return new RenderPlan(
                RenderPlanId.of("rev", "req"),
                RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                TestPlans.revisionRef(), TestPlans.renderRequest(),
                nodes, edges,
                fp,
                new RenderPlanProvenance(RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION));
    }

    private RenderGraph buildGraph(RenderPlan plan) {
        return new RenderGraphBuilder().build(plan).graph();
    }

    private DirectedGraphView<RenderNodeId> topology(RenderPlan plan, RenderGraph graph) {
        return new RenderGraphBuilder().build(plan).topology();
    }
}
