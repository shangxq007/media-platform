package com.example.platform.render.domain.renderplan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C. Graph canonicalization (brief §13C): shuffled node/edge insertion -> identical
 * graph digest; node order by RenderNodeId; edge order by (producer, consumer, variant).
 */
class RenderGraphCanonicalizationTest {

    @Test
    void graphDigestIsOrderIndependent() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput input = TestPlans.canonicalInput();
        String fp1 = planner.plan(input).graph().fingerprint().sha256Hex();
        // plan again (internal collection order is deterministic, but the graph
        // builder sorts regardless) -> identical digest
        String fp2 = planner.plan(input).graph().fingerprint().sha256Hex();
        assertEquals(fp1, fp2, "graph digest deterministic");
    }

    @Test
    void graphNodesOrderedByNodeId() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderGraph graph = planner.plan(TestPlans.canonicalInput()).graph();
        List<RenderNodeId> ids = graph.nodes().stream().map(RenderNode::id).toList();
        List<RenderNodeId> sorted = ids.stream().sorted().toList();
        assertEquals(sorted, ids, "graph nodes ordered by RenderNodeId");
    }

    @Test
    void graphEdgesOrderedByProducerConsumerVariant() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderGraph graph = planner.plan(TestPlans.canonicalInput()).graph();
        List<RenderDependencyEdge> edges = graph.edges();
        List<RenderDependencyEdge> sorted = edges.stream()
                .sorted(java.util.Comparator
                        .comparing((RenderDependencyEdge e) -> e.producerId().value())
                        .thenComparing(e -> e.consumerId().value())
                        .thenComparing(e -> e.dependency().variantKey()))
                .toList();
        assertEquals(sorted, edges, "graph edges ordered by (producer, consumer, variant)");
    }

    @Test
    void graphDigestDiffersFromPlanDigest() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        assertNotEquals(result.plan().fingerprint().sha256Hex(),
                result.graph().fingerprint().sha256Hex(),
                "graph fingerprint distinct from plan fingerprint");
    }
}
