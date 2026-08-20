package com.example.platform.render.domain.renderplan;

import com.example.platform.render.domain.renderplan.graph.RenderGraphBuilder;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidationResult;
import com.example.platform.render.domain.renderplan.graph.RenderGraphValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H. Scale sanity (brief §13H): chain of 500 nodes (499 edges) validates + topo
 * orders within bounded time (< 5s).
 */
class RenderPlanScaleSanityTest {

    @Test
    void chainOfFiveHundredNodesValidatesAndOrdersQuickly() {
        int n = 500;
        List<RenderNode> nodes = new ArrayList<>();
        List<RenderDependencyEdge> edges = new ArrayList<>();
        RenderNodeId first = null;
        RenderNodeId prev = null;
        for (int i = 0; i < n; i++) {
            RenderNodeId id = RenderNodeId.of(new RenderNodeKind.Effect(),
                    new RenderComponentPath(RenderComponentKind.EFFECT, List.of("c1", "e" + i)),
                    "blur", "fp-" + i);
            RenderNode node = new RenderNode(id, new RenderNodeKind.Effect(),
                    new RenderComponentPath(RenderComponentKind.EFFECT, List.of("c1", "e" + i)),
                    "blur",
                    List.of(),
                    List.of(RenderCapabilityVocabulary.forEffect(
                            com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.GAUSSIAN_BLUR)),
                    List.of(), List.of(), List.of(), Optional.empty());
            nodes.add(node);
            if (prev != null) {
                edges.add(new RenderDependencyEdge(prev, id, new RenderDependency.EffectInput()));
            } else {
                first = id;
            }
            prev = id;
        }

        EffectSemanticReference effectRef = TestPlans.testEffectReference();
        RenderPlanFingerprint fp = RenderPlanFingerprintCalculator.compute(
                TestPlans.revisionRef(), effectRef, TestPlans.renderRequest(), nodes, edges);
        RenderPlan plan = new RenderPlan(
                RenderPlanId.of("rev", "req"),
                RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                TestPlans.revisionRef(), effectRef, TestPlans.renderRequest(),
                nodes, edges,
                fp,
                new RenderPlanProvenance(RenderPlanCanonicalCodec.PLAN_FORMAT_VERSION,
                        TestPlans.revisionRef().revisionId(), effectRef));

        long start = System.currentTimeMillis();
        RenderGraph graph = new RenderGraphBuilder().build(plan).graph();
        RenderGraphValidationResult validation = new RenderGraphValidator().validate(plan, graph,
                new RenderGraphBuilder().build(plan).topology());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(validation.valid(), "chain graph valid");
        assertEquals(n, graph.nodes().size(), "node count == 500");
        assertEquals(n, new RenderGraphBuilder().build(plan).topology().nodeCount(), "topology node count == 500");
        assertTrue(elapsed < 5000, "total runtime < 5s (was " + elapsed + "ms)");
    }
}
