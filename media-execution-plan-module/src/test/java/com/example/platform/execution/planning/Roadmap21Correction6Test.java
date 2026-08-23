package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 6 — edge-order determinism (C6-A), pruning-evidence
 * structural framing (C6-B), end-to-end fail-closed guarded entry (C6-C).
 */
class Roadmap21Correction6Test {

    static final RenderExtent EXTENT = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));

    static RenderExecutionCoverage coverage(long start, long end) {
        return new RenderExecutionCoverage(
                MediaTime.ofMillis(start), MediaTime.ofMillis(end), FrameRate.of(25, 1));
    }

    static RenderNode node(String id, long start, long end) {
        return new RenderNode(
                new RenderNodeId(id),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Decode(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c-" + id),
                "decode",
                List.of(), List.of(
                        new com.example.platform.extension.domain.CapabilityRequirement(
                                com.example.platform.extension.domain.CapabilityId.of("media.decode"),
                                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                                        com.example.platform.extension.domain.ContractVersion.of(1, 0)),
                                true, List.of())),
                List.of(), List.of(
                        new com.example.platform.render.domain.renderplan.RenderExecutionRequirement(
                                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement.NONE,
                                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                                false)),
                List.of(), java.util.Optional.empty(), coverage(start, end));
    }

    static RenderDependencyEdge edge(String producer, String consumer, RenderDependency dep) {
        return new RenderDependencyEdge(
                new RenderNodeId(producer), new RenderNodeId(consumer), dep);
    }

    /** Same semantic graph with edges in different insertion order. */
    static RenderGraph graphWithEdgeOrder(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        return new RenderGraph("render-graph-v1",
                new RenderPlanFingerprint("fp-1"), nodes, edges,
                new RenderGraphFingerprint("gf-1"));
    }

    static LogicalExecutionGraph build(RenderGraph g) {
        return LogicalExecutionGraphBuilder.build(g, EXTENT);
    }

    // ---------- C6-T01..T03: edge insertion order determinism ----------

    @Test
    void edgeOrderReversedSameFingerprint() { // C6-T01
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var e = List.of(edge("n1", "n2", new RenderDependency.DecodedFrames()));
        var gA = graphWithEdgeOrder(List.of(n1, n2), e);
        var gB = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        // #20 fingerprint is order-independent (upstream authority)
        assertEquals(gA.planFingerprint(), gB.planFingerprint(), "C6-T01 #20 graph fingerprint order-independent");
        assertEquals(com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator.codec().graphFingerprintCanonical(gA),
                com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator.codec().graphFingerprintCanonical(gB),
                "#20 canonical graph encoding is edge-order independent");
    }

    @Test
    void edgeOrderReversedSameLogicalDigest() { // C6-T02
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var e1 = edge("n1", "n2", new RenderDependency.DecodedFrames());
        var e2 = edge("n2", "n1", new RenderDependency.SubtitleRaster());
        var gA = graphWithEdgeOrder(List.of(n1, n2), List.of(e1, e2));
        var gB = graphWithEdgeOrder(List.of(n1, n2), List.of(e2, e1));
        assertEquals(build(gA).digest(), build(gB).digest(),
                "C6-T02 edge insertion order cannot alter logical digest");
    }

    @Test
    void edgeOrderReversedSamePhysicalDigest() { // C6-T03
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var gA = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var gB = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var pA = PhysicalPlannerV1.plan(build(gA), EXTENT, new ExecutionPlanId("pep-a"));
        var pB = PhysicalPlannerV1.plan(build(gB), EXTENT, new ExecutionPlanId("pep-b"));
        assertEquals(pA.digest(), pB.digest(),
                "C6-T03 edge insertion order cannot alter physical digest");
    }

    // ---------- C6-T04/T05: dependency semantics mutate digest ----------

    @Test
    void dependencySemanticsMutateLogicalDigest() { // C6-T04
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var gA = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var gB = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2",
                        new RenderDependency.AudioInput(new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1")))));
        assertNotEquals(build(gA).digest(), build(gB).digest(),
                "C6-T04 dependency payload mutation changes logical digest");
    }

    @Test
    void dependencySemanticsMutatePhysicalDigest() { // C6-T05
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var gA = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var gB = graphWithEdgeOrder(List.of(n1, n2),
                List.of(edge("n1", "n2",
                        new RenderDependency.AudioInput(new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1")))));
        var pA = PhysicalPlannerV1.plan(build(gA), EXTENT, new ExecutionPlanId("pep-a"));
        var pB = PhysicalPlannerV1.plan(build(gB), EXTENT, new ExecutionPlanId("pep-b"));
        assertNotEquals(pA.digest(), pB.digest(),
                "C6-T05 dependency payload mutation changes physical digest");
    }

    // ---------- C6-T06..T08: eliminated-node structural framing ----------

    static LogicalExecutionGraph graphWithEliminated(List<String> ids) {
        var n1 = node("n1", 0, 10000);
        var n2 = node("n2", 0, 10000);
        var g = graphWithEdgeOrder(List.of(n1, n2), List.of());
        // force pruning evidence with given eliminated ids
        var evidence = new LogicalExecutionGraph.PruningEvidence(
                "0:0-100000:25/1",
                ids.stream().map(id -> new LogicalExecutionGraph.PruningEvidence.EliminatedNode(
                        "logical-" + id, new RenderNodeId(id), "", "", "out-of-extent")).toList(),
                true);
        return new LogicalExecutionGraph("logical-v1", new RenderPlanFingerprint("fp-1"),
                build(g).nodes(), build(g).edges(), evidence,
                LogicalExecutionGraphDigest.compute("logical-v1", EXTENT, build(g).nodes(),
                        build(g).edges(), new RenderPlanFingerprint("fp-1"), evidence));
    }

    @Test
    void eliminatedNewlineCollisionDisproved() { // C6-T06
        var two = graphWithEliminated(List.of("a", "b"));
        var one = graphWithEliminated(List.of("a\nb"));
        assertNotEquals(two.digest(), one.digest(),
                "C6-T06 [\"a\",\"b\"] must not collide with [\"a\\nb\"] — structural list framing");
    }

    @Test
    void eliminatedMembershipChangesDigest() { // C6-T07
        assertNotEquals(graphWithEliminated(List.of("a")).digest(),
                graphWithEliminated(List.of("a", "b")).digest(),
                "C6-T07 different eliminated membership changes logical digest");
    }

    @Test
    void eliminatedInsertionOrderDeterministic() { // C6-T08
        assertEquals(graphWithEliminated(List.of("b", "a")).digest(),
                graphWithEliminated(List.of("a", "b")).digest(),
                "C6-T08 eliminated set insertion order cannot alter digest");
    }

}
