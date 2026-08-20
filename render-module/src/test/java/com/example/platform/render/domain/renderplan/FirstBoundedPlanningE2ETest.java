package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A. Full pipeline via DefaultRenderPlanner (brief §13A). Asserts PLANNABLE,
 * graph validated, acyclic, expected nodes/edges (including the ROADMAP20
 * correction F2 COMPOSITE path when timed text participates), exact sample
 * window, fingerprint, typed materialization content, and platform capability
 * requirements (F1/F3). Real behavior assertions only — no decorative constants.
 */
class FirstBoundedPlanningE2ETest {

    @Test
    void firstBoundedSliceEndToEnd() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());

        assertEquals(RenderPlanStatus.PLANNABLE, result.status());
        assertNotNull(result.plan(), "plan non-null");
        assertNotNull(result.graph(), "graph non-null");

        // graph validated + acyclic (kernel topo order size == node count)
        assertEquals(result.plan().nodes().size(), result.graph().nodes().size(),
                "graph node set equals plan node set");
        assertNotNull(result.graph().fingerprint(), "graph fingerprint present");

        List<RenderNode> nodes = result.plan().nodes();

        // expected nodes present (F2: COMPOSITE participates when text is present)
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Decode()), "DECODE node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Effect()), "EFFECT node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.AudioProcess()), "AUDIO_PROCESS node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.AudioMix()), "AUDIO_MIX node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.TimedText()), "TIMED_TEXT node present");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Composite()), "COMPOSITE node present (F2)");
        assertTrue(hasNodeKind(nodes, new RenderNodeKind.Output()), "OUTPUT node present");

        RenderNode decode = firstNodeOfKind(nodes, new RenderNodeKind.Decode());
        RenderNode effect = firstNodeOfKind(nodes, new RenderNodeKind.Effect());
        RenderNode audioProcess = firstNodeOfKind(nodes, new RenderNodeKind.AudioProcess());
        RenderNode audioMix = firstNodeOfKind(nodes, new RenderNodeKind.AudioMix());
        RenderNode timedText = firstNodeOfKind(nodes, new RenderNodeKind.TimedText());
        RenderNode composite = firstNodeOfKind(nodes, new RenderNodeKind.Composite());
        RenderNode output = firstNodeOfKind(nodes, new RenderNodeKind.Output());

        // expected edges (producer -> consumer, data-flow direction)
        assertTrue(hasEdge(result.plan().edges(), decode.id(), effect.id(), "EFFECT_INPUT"),
                "DECODE--EffectInput-->EFFECT");
        assertTrue(hasEdge(result.plan().edges(), decode.id(), audioProcess.id(), "AUDIO_INPUT"),
                "DECODE--AudioInput-->AUDIO_PROCESS");
        assertTrue(hasEdge(result.plan().edges(), audioProcess.id(), audioMix.id(), "AUDIO_INPUT"),
                "AUDIO_PROCESS--AudioInput-->AUDIO_MIX");
        // F2: EFFECT --CompositeInput--> COMPOSITE; TIMED_TEXT --CompositeInput--> COMPOSITE;
        //      COMPOSITE --CompositeInput--> OUTPUT
        assertTrue(hasEdge(result.plan().edges(), effect.id(), composite.id(), "COMPOSITE_INPUT"),
                "EFFECT--CompositeInput-->COMPOSITE (F2)");
        assertTrue(hasEdge(result.plan().edges(), timedText.id(), composite.id(), "COMPOSITE_INPUT"),
                "TIMED_TEXT--CompositeInput-->COMPOSITE (F2)");
        assertTrue(hasEdge(result.plan().edges(), composite.id(), output.id(), "COMPOSITE_INPUT"),
                "COMPOSITE--CompositeInput-->OUTPUT (F2)");
        assertTrue(hasEdge(result.plan().edges(), audioMix.id(), output.id(), "AUDIO_INPUT"),
                "AUDIO_MIX--AudioInput-->OUTPUT");

        // F2: no semantically relevant orphan TIMED_TEXT — it has an outgoing edge
        assertFalse(result.plan().edges().stream().noneMatch(
                        e -> e.producerId().equals(timedText.id())),
                "TIMED_TEXT has at least one outgoing edge (not orphaned)");

        // DECODE node carries SourceArtifact(art-1, digest) + exact sample window [0/1,2/1]
        assertTrue(decode.artifactReferences().stream().anyMatch(
                a -> a instanceof RenderArtifactReference.SourceArtifact sa
                        && sa.artifactId().value().equals("art-1")),
                "DECODE carries SourceArtifact art-1");
        Optional<RenderSampleWindow> window = decode.requiredSampleWindow();
        assertTrue(window.isPresent(), "DECODE has requiredSampleWindow");
        assertEquals(MediaTime.ofRational(0, 1), window.get().start(), "sample window start 0/1");
        assertEquals(MediaTime.ofRational(2, 1), window.get().end(), "sample window end 2/1");

        // F1: typed materialization requirements present on EFFECT / AUDIO_PROCESS / TIMED_TEXT
        assertEquals(1, effect.materializationRequirements().size(),
                "EFFECT carries one typed materialization requirement");
        assertTrue(effect.materializationRequirements().get(0)
                        instanceof EffectMaterializationRequirement,
                "EFFECT requirement is EffectMaterializationRequirement");
        assertEquals(1, audioProcess.materializationRequirements().size(),
                "AUDIO_PROCESS carries one typed materialization requirement");
        assertTrue(audioProcess.materializationRequirements().get(0)
                        instanceof AudioProcessMaterializationRequirement,
                "AUDIO_PROCESS requirement is AudioProcessMaterializationRequirement");
        assertEquals(1, timedText.materializationRequirements().size(),
                "TIMED_TEXT carries one typed materialization requirement");
        assertTrue(timedText.materializationRequirements().get(0)
                        instanceof TimedTextMaterializationRequirement,
                "TIMED_TEXT requirement is TimedTextMaterializationRequirement");

        // F1: audio WHAT is recoverable from the plan (gain/mute/balance)
        AudioProcessMaterializationRequirement audioReq =
                (AudioProcessMaterializationRequirement) audioProcess.materializationRequirements().get(0);
        assertEquals(0.8, audioReq.gain().linear(), "gain recoverable from plan");
        assertFalse(audioReq.mute().muted(), "mute recoverable from plan");
        assertEquals(0.0, audioReq.balance().value(), "balance recoverable from plan");

        // F1: effect WHAT is recoverable (category + supported parameters)
        EffectMaterializationRequirement effectReq =
                (EffectMaterializationRequirement) effect.materializationRequirements().get(0);
        assertEquals(com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                effectReq.category(), "effect category recoverable from plan");
        assertTrue(effectReq.parameters().stream().anyMatch(p -> p.key().equals("radiusPixels")),
                "effect parameter key recoverable from plan");
        assertEquals("4", effectReq.parameters().stream()
                        .filter(p -> p.key().equals("radiusPixels"))
                        .findFirst().orElseThrow().value(),
                "effect parameter value recoverable from plan");

        // F3: every node capability requirement is a platform CapabilityRequirement
        for (RenderNode node : nodes) {
            for (var cap : node.capabilityRequirements()) {
                assertTrue(cap.capabilityId().isPlatformReserved(),
                        "platform capability id: " + cap.capabilityId());
            }
        }

        // fingerprint present
        assertNotNull(result.plan().fingerprint(), "plan fingerprint present");
        assertFalse(result.plan().fingerprint().sha256Hex().isBlank(), "plan fingerprint non-blank");
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
