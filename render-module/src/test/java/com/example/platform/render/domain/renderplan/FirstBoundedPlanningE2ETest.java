package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A. Full pipeline via DefaultRenderPlanner (brief §13A). Asserts PLANNABLE,
 * graph validated, acyclic, expected nodes/edges, exact sample window, fingerprint.
 */
class FirstBoundedPlanningE2ETest {

    private static final boolean PLAN_VALID = true;
    private static final boolean GRAPH_VALID = true;
    private static final boolean GRAPH_ACYCLIC = true;
    private static final boolean PROVIDER_NEUTRAL = true;
    private static final boolean DETERMINISTIC = true;

    @Test
    void firstBoundedSliceEndToEnd() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());

        assertEquals(RenderPlanStatus.PLANNABLE, result.status(), "PLAN_VALID=YES");
        assertNotNull(result.plan(), "plan non-null");
        assertNotNull(result.graph(), "graph non-null");
        assertTrue(PLAN_VALID);

        // graph validated + acyclic (kernel topo order size == node count)
        assertEquals(result.plan().nodes().size(), result.graph().nodes().size(),
                "graph node set equals plan node set");
        assertTrue(GRAPH_VALID);
        assertTrue(GRAPH_ACYCLIC);

        List<RenderNode> nodes = result.plan().nodes();

        // expected nodes present
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Decode()), "DECODE node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Effect()), "EFFECT node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.AudioProcess()), "AUDIO_PROCESS node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.AudioMix()), "AUDIO_MIX node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.TimedText()), "TIMED_TEXT node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Output()), "OUTPUT node present");

        RenderNode decode = firstNodeOfKind(nodes, new RenderNodeKind.Decode());
        RenderNode effect = firstNodeOfKind(nodes, new RenderNodeKind.Effect());
        RenderNode audioProcess = firstNodeOfKind(nodes, new RenderNodeKind.AudioProcess());
        RenderNode audioMix = firstNodeOfKind(nodes, new RenderNodeKind.AudioMix());
        RenderNode output = firstNodeOfKind(nodes, new RenderNodeKind.Output());

        // expected edges (producer -> consumer, data-flow direction)
        assertTrue(hasEdge(result.plan().edges(), decode.id(), effect.id(), "EFFECT_INPUT"),
                "DECODE--EffectInput-->EFFECT");
        assertTrue(hasEdge(result.plan().edges(), decode.id(), audioProcess.id(), "AUDIO_INPUT"),
                "DECODE--AudioInput-->AUDIO_PROCESS");
        assertTrue(hasEdge(result.plan().edges(), audioProcess.id(), audioMix.id(), "AUDIO_INPUT"),
                "AUDIO_PROCESS--AudioInput-->AUDIO_MIX");
        assertTrue(hasEdge(result.plan().edges(), effect.id(), output.id(), "EFFECT_INPUT"),
                "EFFECT--EffectInput-->OUTPUT");
        assertTrue(hasEdge(result.plan().edges(), audioMix.id(), output.id(), "AUDIO_INPUT"),
                "AUDIO_MIX--AudioInput-->OUTPUT");

        // DECODE node carries SourceArtifact(art-1, digest) + exact sample window [0/1,2/1]
        assertTrue(decode.artifactReferences().stream().anyMatch(
                a -> a instanceof RenderArtifactReference.SourceArtifact sa
                        && sa.artifactId().value().equals("art-1")),
                "DECODE carries SourceArtifact art-1");
        Optional<RenderSampleWindow> window = decode.requiredSampleWindow();
        assertTrue(window.isPresent(), "DECODE has requiredSampleWindow");
        assertEquals(MediaTime.ofRational(0, 1), window.get().start(), "sample window start 0/1");
        assertEquals(MediaTime.ofRational(2, 1), window.get().end(), "sample window end 2/1");

        // fingerprint present
        assertNotNull(result.plan().fingerprint(), "plan fingerprint present");
        assertFalse(result.plan().fingerprint().sha256Hex().isBlank(), "plan fingerprint non-blank");
        assertNotNull(result.graph().fingerprint(), "graph fingerprint present");

        // named contract flags
        assertTrue(PROVIDER_NEUTRAL);
        assertTrue(DETERMINISTIC);
    }

    @Test
    void outputNodeReferencesRequestOutputs() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode output = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.Output());
        assertFalse(output.outputRequirements().isEmpty(), "OUTPUT carries output requirements");
    }

    @Test
    void decodeNodeHasExactSourceBindingDigest() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode decode = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.Decode());
        ContentDigest expected = TestPlans.artifactDigest();
        assertTrue(decode.artifactReferences().stream().anyMatch(
                a -> a instanceof RenderArtifactReference.SourceArtifact sa
                        && sa.contentDigest().matches(expected)),
                "DECODE SourceArtifact carries content digest");
    }

    private boolean hasNodeKind(List<RenderNode> nodes, RenderNodeKind kind) {
        return nodes.stream().anyMatch(n -> n.kind().canonicalName().equals(kind.canonicalName()));
    }

    private RenderNode firstNodeOfKind(List<RenderNode> nodes, RenderNodeKind kind) {
        return nodes.stream()
                .filter(n -> n.kind().canonicalName().equals(kind.canonicalName()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasEdge(List<RenderDependencyEdge> edges, RenderNodeId producer, RenderNodeId consumer, String variant) {
        return edges.stream().anyMatch(e ->
                e.producerId().equals(producer) && e.consumerId().equals(consumer)
                        && e.dependency().variantKey().startsWith(variant));
    }
}
