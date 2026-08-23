package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 3 — B1 graph-closed extent pruning (T-C1..T-C13),
 * production-shaped graphs. Pruning compares typed RenderExecutionCoverage
 * (timeline coords) vs RenderExtent only; RenderSampleWindow (source coords)
 * never participates; ALL_PRODUCERS_ELIMINATED node pruning is FORBIDDEN.
 */
class Roadmap21GraphClosureTest {

    static final RenderPlanFingerprint FP = new RenderPlanFingerprint("fp-1");
    static final RenderExtent EXTENT = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));

    static RenderExecutionCoverage cov(long s, long e) {
        return new RenderExecutionCoverage(MediaTime.ofMillis(s), MediaTime.ofMillis(e), FrameRate.of(25, 1));
    }

    static RenderNode n(String id, String op, RenderNodeKind kind, RenderExecutionCoverage coverage) {
        return new RenderNode(new RenderNodeId(id), kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "c-" + id), op,
                List.of(), List.of(
                        new CapabilityRequirement(CapabilityId.of("media." + op),
                                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of())),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(new RenderExecutionRequirement(GpuRequirement.NONE,
                        RenderDeterminismClass.DETERMINISTIC, false)),
                List.of(), Optional.empty(), coverage);
    }

    static RenderDependencyEdge e(String p, String c, RenderDependency d) {
        return new RenderDependencyEdge(new RenderNodeId(p), new RenderNodeId(c), d);
    }

    static LogicalExecutionGraph build(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        return LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, nodes, edges, new RenderGraphFingerprint("gf-1")),
                EXTENT);
    }

    // T-C1: out-of-extent DECODE feeding OUTPUT -> valid extent-limited result
    @Test
    void outOfExtentDecodeFeedingOutputGraphClosed() {
        var decode = n("d1", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var out = n("o1", "encode", new RenderNodeKind.Output(), null);
        var g = build(List.of(decode, out), List.of(e("d1", "o1", new RenderDependency.EffectInput())));
        assertEquals(1, g.nodes().size(), "decode pruned, output survives");
        assertEquals(0, g.edges().size(), "edge from eliminated producer removed — no dangling producer");
        assertTrue(g.pruningEvidence().pruningApplied());
        // graph-closure invariant: every surviving edge endpoint survives
        var ids = g.nodes().stream().map(x -> x.logicalNodeId()).collect(java.util.stream.Collectors.toSet());
        for (var edge : g.edges()) {
            assertTrue(ids.contains(edge.producerLogicalNodeId()), "producer survives");
            assertTrue(ids.contains(edge.consumerLogicalNodeId()), "consumer survives");
        }
    }

    // T-C2: out-of-extent DECODE -> EFFECT -> OUTPUT chain
    @Test
    void outOfExtentChainGraphClosed() {
        var decode = n("d1", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var effect = n("e1", "blur", new RenderNodeKind.Effect(), cov(50000, 60000));
        var out = n("o1", "encode", new RenderNodeKind.Output(), null);
        var g = build(List.of(decode, effect, out),
                List.of(e("d1", "e1", new RenderDependency.DecodedFrames()),
                        e("e1", "o1", new RenderDependency.EffectInput())));
        assertEquals(1, g.nodes().size(), "out-of-extent decode+effect pruned, output survives");
        assertTrue(g.edges().isEmpty(), "no dangling edges");
    }

    // T-C3: one in-extent + one out-of-extent branch feeding aggregate
    @Test
    void mixedBranchAggregateKeepsContributingBranch() {
        var inDecode = n("d1", "decode", new RenderNodeKind.Decode(), cov(0, 10000));
        var outDecode = n("d2", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var composite = n("c1", "composite", new RenderNodeKind.Composite(), null);
        var g = build(List.of(inDecode, outDecode, composite),
                List.of(e("d1", "c1", new RenderDependency.EffectInput()),
                        e("d2", "c1", new RenderDependency.EffectInput())));
        assertEquals(2, g.nodes().size(), "in-extent branch + aggregate survive");
        assertEquals(1, g.edges().size(), "only contributing branch edge remains");
        assertEquals("ln-d1", g.edges().get(0).producerLogicalNodeId(),
                "required in-extent input preserved — nothing silently lost");
    }

    // T-C4: TIMED_TEXT fully outside extent — null coverage => never pruned
    @Test
    void timedTextOutsideExtentSurvivesNullCoverage() {
        var text = n("t1", "raster", new RenderNodeKind.TimedText(), null);
        var composite = n("c1", "composite", new RenderNodeKind.Composite(), null);
        var g = build(List.of(text, composite),
                List.of(e("t1", "c1", new RenderDependency.SubtitleRaster())));
        assertEquals(2, g.nodes().size(),
                "TIMED_TEXT has null coverage (no timeline interval in #20 TextElement) -> never pruned");
    }

    // T-C5: TIMED_TEXT with coverage overlapping extent survives
    @Test
    void timedTextPartialOverlapSurvives() {
        var text = n("t1", "raster", new RenderNodeKind.TimedText(), cov(5000, 20000));
        var g = build(List.of(text), List.of());
        assertEquals(1, g.nodes().size());
    }

    // T-C6: AUDIO_PROCESS/AUDIO_MIX with routed clip outside extent -> no false dangling
    @Test
    void audioPathOutOfExtentGraphClosed() {
        var decode = n("d1", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var audio = n("a1", "gain", new RenderNodeKind.AudioProcess(), cov(50000, 60000));
        var mix = n("m1", "mix", new RenderNodeKind.AudioMix(), null);
        var g = build(List.of(decode, audio, mix),
                List.of(e("d1", "a1", new RenderDependency.AudioInput(
                                new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1"))),
                        e("a1", "m1", new RenderDependency.AudioInput(
                                new com.example.platform.audio.domain.mix.AudioMixInput("synthetic", "synthetic")))));
        assertEquals(1, g.nodes().size(), "out-of-extent audio path pruned, mix survives");
        assertTrue(g.edges().isEmpty(), "no dangling edges");
    }

    // T-C7: AUDIO_PROCESS overlapping extent survives
    @Test
    void audioPathOverlappingExtentSurvives() {
        var decode = n("d1", "decode", new RenderNodeKind.Decode(), cov(0, 10000));
        var audio = n("a1", "gain", new RenderNodeKind.AudioProcess(), cov(0, 10000));
        var g = build(List.of(decode, audio),
                List.of(e("d1", "a1", new RenderDependency.AudioInput(
                        new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1")))));
        assertEquals(2, g.nodes().size());
        assertEquals(1, g.edges().size());
    }

    // T-C8: OUTPUT/COMPOSITE with mixed surviving+eliminated branches stays valid
    @Test
    void compositeOutputMixedBranchesValid() {
        var inDecode = n("d1", "decode", new RenderNodeKind.Decode(), cov(0, 10000));
        var outDecode = n("d2", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var effect = n("e1", "blur", new RenderNodeKind.Effect(), cov(0, 10000));
        var composite = n("c1", "composite", new RenderNodeKind.Composite(), null);
        var out = n("o1", "encode", new RenderNodeKind.Output(), null);
        var g = build(List.of(inDecode, outDecode, effect, composite, out),
                List.of(e("d1", "e1", new RenderDependency.DecodedFrames()),
                        e("d2", "c1", new RenderDependency.EffectInput()),
                        e("e1", "c1", new RenderDependency.EffectInput()),
                        e("c1", "o1", new RenderDependency.CompositeInput())));
        assertEquals(4, g.nodes().size(), "in-branch + aggregate + output survive");
        assertEquals(3, g.edges().size(), "eliminated branch edge removed; others preserved");
    }

    // T-C9: non-zero source offset — coverage [0,10] vs source window [100,110]
    @Test
    void nonZeroSourceOffsetDecodeSurvives() {
        var decode = n("d1", "decode", new RenderNodeKind.Decode(), cov(0, 10000));
        // source sample window [100,110] (source coords) must NOT be compared
        var node = new RenderNode(decode.id(), decode.kind(),
                decode.componentPath(), decode.operationKey(), decode.artifactReferences(),
                decode.capabilityRequirements(), decode.outputRequirements(),
                decode.executionRequirements(), decode.materializationRequirements(),
                Optional.of(new com.example.platform.render.domain.renderplan.RenderSampleWindow(
                        MediaTime.ofMillis(100000), MediaTime.ofMillis(110000), FrameRate.of(25, 1))),
                cov(0, 10000));
        var g = build(List.of(node), List.of());
        assertEquals(1, g.nodes().size(), "source-offset sample window must NOT false-prune");
        assertFalse(g.pruningEvidence().pruningApplied());
    }

    // T-C10/T-C11: reverse/freeze mapping — covered by ContractBehaviorTest; here: boundary
    // T-C12: coverage.end == extent.start -> disjoint; coverage.start == extent.end -> disjoint
    @Test
    void boundaryExactDisjointness() {
        // coverage [0,5000].end == extent [5000,10000].start -> disjoint (half-open)
        var a = n("a1", "x", new RenderNodeKind.Source(), cov(0, 5000));
        var extentA = new RenderExtent(MediaTime.ofMillis(5000), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var ga = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(a), List.of(),
                        new RenderGraphFingerprint("gf-1")), extentA);
        assertEquals(0, ga.nodes().size(), "coverage.end == extent.start is provably outside -> pruned");

        // coverage [10000,20000].start == extent [0,10000].end -> disjoint (half-open)
        var b = n("b1", "y", new RenderNodeKind.Source(), cov(10000, 20000));
        var gb = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(b), List.of(),
                        new RenderGraphFingerprint("gf-1")), EXTENT);
        assertEquals(0, gb.nodes().size(), "coverage.start == extent.end is provably outside -> pruned");
    }

    // T-C13: same frozen input -> same eliminated set, same edge set, same digest
    @Test
    void deterministicPruningAndDigest() {
        var d1 = n("d1", "decode", new RenderNodeKind.Decode(), cov(0, 10000));
        var d2 = n("d2", "decode", new RenderNodeKind.Decode(), cov(50000, 60000));
        var out = n("o1", "encode", new RenderNodeKind.Output(), null);
        var edges = List.of(e("d1", "o1", new RenderDependency.EffectInput()),
                e("d2", "o1", new RenderDependency.EffectInput()));
        var g1 = build(List.of(d1, d2, out), edges);
        var g2 = build(List.of(d1, d2, out), edges);
        assertEquals(g1.nodes().stream().map(x -> x.logicalNodeId()).toList(),
                g2.nodes().stream().map(x -> x.logicalNodeId()).toList(),
                "same eliminated node set");
        assertEquals(g1.edges().stream().map(x -> x.edgeId().value()).toList(),
                g2.edges().stream().map(x -> x.edgeId().value()).toList(),
                "same surviving edge set");
        assertEquals(g1.digest(), g2.digest(), "same digest");
    }
}
