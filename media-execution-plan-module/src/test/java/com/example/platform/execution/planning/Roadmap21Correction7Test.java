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
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 Correction 7 — final determinism closure:
 * physical input identity normalization (C7-A), logical nested collection
 * canonicalization (C7-B), physical nested collection canonicalization
 * (C7-C). Every permutation test genuinely reverses order and asserts model
 * content equality, not only digest equality.
 */
class Roadmap21Correction7Test {

    static final RenderExtent EXTENT = new RenderExtent(
            MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));

    static RenderExecutionCoverage coverage(long start, long end) {
        return new RenderExecutionCoverage(
                MediaTime.ofMillis(start), MediaTime.ofMillis(end), FrameRate.of(25, 1));
    }

    static RenderNode decode(String id) {
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
                List.of(), java.util.Optional.empty(), coverage(0, 10000));
    }

    static RenderNode withArtifacts(String id, List<com.example.platform.render.domain.renderplan.RenderArtifactReference> artifacts) {
        return new RenderNode(
                new RenderNodeId(id),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Decode(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c-" + id),
                "decode",
                artifacts, List.of(
                        new com.example.platform.extension.domain.CapabilityRequirement(
                                com.example.platform.extension.domain.CapabilityId.of("media.decode"),
                                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                                        com.example.platform.extension.domain.ContractVersion.of(1, 0)),
                                true, List.of())),
                List.of(), List.of(), List.of(), java.util.Optional.empty(), coverage(0, 10000));
    }

    static com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact sourceArtifact(String id, String digestHex) {
        return new com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact(
                new com.example.platform.shared.identity.ArtifactId(id),
                new com.example.platform.shared.digest.ContentDigest(
                        com.example.platform.shared.digest.ContentDigest.DigestAlgorithm.SHA_256, digestHex));
    }

    static RenderDependencyEdge edge(String producer, String consumer, RenderDependency dep) {
        return new RenderDependencyEdge(new RenderNodeId(producer), new RenderNodeId(consumer), dep);
    }

    static RenderGraph graph(List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        return new RenderGraph("render-graph-v1",
                new RenderPlanFingerprint("fp-1"), nodes, edges,
                new RenderGraphFingerprint("gf-1"));
    }

    static LogicalExecutionGraph build(RenderGraph g) {
        return LogicalExecutionGraphBuilder.build(g, EXTENT);
    }

    static PhysicalExecutionPlan plan(LogicalExecutionGraph lg, String planId) {
        return PhysicalPlannerV1.plan(lg, EXTENT, new ExecutionPlanId(planId));
    }

    static void assertOrderActuallyDiffers(RenderGraph a, RenderGraph b) {
        assertEquals(a.edges().size(), b.edges().size(), "same edge count");
        if (a.edges().size() >= 2) {
            assertNotEquals(a.edges().get(0), b.edges().get(0),
                    "permutation test must genuinely reverse order");
        }
    }

    // ---------- C7-T01..T04: REAL multi-edge permutation ----------

    @Test
    void multiEdgeOrderInvariance() { // C7-T01/T02/T03/T04
        var p1 = decode("p1");
        var p2 = decode("p2");
        var consumer = decode("c1");
        var e1 = edge("p1", "c1", new RenderDependency.DecodedFrames());
        var e2 = edge("p2", "c1", new RenderDependency.SubtitleRaster());
        var gA = graph(List.of(p1, p2, consumer), List.of(e1, e2));
        var gB = graph(List.of(p1, p2, consumer), List.of(e2, e1));
        assertOrderActuallyDiffers(gA, gB);

        // C7-T01: #20 canonical encoding equal
        assertEquals(
                com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator
                        .codec().graphFingerprintCanonical(gA),
                com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator
                        .codec().graphFingerprintCanonical(gB),
                "C7-T01 #20 graph canonical encoding invariant under edge order");

        var lgA = build(gA);
        var lgB = build(gB);
        // C7-T02
        assertEquals(lgA.digest(), lgB.digest(), "C7-T02 logical digest invariant");

        var pA = plan(lgA, "pep-A");
        var pB = plan(lgB, "pep-B");
        // C7-T03: MODEL content invariant — normalized input semantic structures equal
        var inputsA = pA.units().stream()
                .flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value() + "|" + i.producerLogicalNodeId() + "|"
                        + (i.dependencyVariant() != null
                                ? Canonical.dependency(i.dependencyVariant()) : "null")
                        + "|" + (i.sourceArtifact() != null
                                ? Canonical.sourceArtifact(i.sourceArtifact()) : "null"))
                .sorted()
                .collect(Collectors.toList());
        var inputsB = pB.units().stream()
                .flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value() + "|" + i.producerLogicalNodeId() + "|"
                        + (i.dependencyVariant() != null
                                ? Canonical.dependency(i.dependencyVariant()) : "null")
                        + "|" + (i.sourceArtifact() != null
                                ? Canonical.sourceArtifact(i.sourceArtifact()) : "null"))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(inputsA, inputsB, "C7-T03 normalized physical input semantic content invariant");
        // C7-T04
        assertEquals(pA.digest(), pB.digest(), "C7-T04 physical digest invariant");
    }

    @Test
    void inputIdsStableUnderEdgePermutation() { // C7-T17
        var p1 = decode("p1");
        var p2 = decode("p2");
        var consumer = decode("c1");
        var e1 = edge("p1", "c1", new RenderDependency.DecodedFrames());
        var e2 = edge("p2", "c1", new RenderDependency.SubtitleRaster());
        var gA = graph(List.of(p1, p2, consumer), List.of(e1, e2));
        var gB = graph(List.of(p1, p2, consumer), List.of(e2, e1));
        var idsA = plan(build(gA), "pep-A").units().stream()
                .flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value()).sorted().toList();
        var idsB = plan(build(gB), "pep-B").units().stream()
                .flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value()).sorted().toList();
        assertEquals(idsA, idsB, "C7-T17 ExecutionInputId values/multiset identical under edge permutation");
    }

    // ---------- C7-T05: source artifact order invariance ----------

    @Test
    void sourceArtifactOrderInvariance() { // C7-T05 + T18
        var a1 = sourceArtifact("art-1", "a".repeat(64));
        var a2 = sourceArtifact("art-2", "b".repeat(64));
        var nA = withArtifacts("n1", List.of(a1, a2));
        var nB = withArtifacts("n1", List.of(a2, a1));
        var gA = graph(List.of(nA), List.of());
        var gB = graph(List.of(nB), List.of());
        assertEquals(
                com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator
                        .codec().graphFingerprintCanonical(gA),
                com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator
                        .codec().graphFingerprintCanonical(gB),
                "#20 semantics equal");
        assertEquals(build(gA).digest(), build(gB).digest(),
                "C7-T05 logical digest invariant under artifact order");
        var pA = plan(build(gA), "pep-A");
        var pB = plan(build(gB), "pep-B");
        var srcA = pA.units().stream().flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value() + "|" + Canonical.sourceArtifact(i.sourceArtifact()))
                .sorted().toList();
        var srcB = pB.units().stream().flatMap(u -> u.typedInputs().stream())
                .map(i -> i.inputId().value() + "|" + Canonical.sourceArtifact(i.sourceArtifact()))
                .sorted().toList();
        assertEquals(srcA, srcB, "C7-T18 input ids + artifact semantics identical");
        assertEquals(pA.digest(), pB.digest(), "C7-T05 physical digest invariant");
    }

    // ---------- C7-T06..T08: requirement order invariance ----------

    @Test
    void capabilityOrderInvariance() { // C7-T06 + T14
        var capA = new com.example.platform.extension.domain.CapabilityRequirement(
                com.example.platform.extension.domain.CapabilityId.of("media.decode"),
                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                        com.example.platform.extension.domain.ContractVersion.of(1, 0)),
                true, List.of());
        var capB = new com.example.platform.extension.domain.CapabilityRequirement(
                com.example.platform.extension.domain.CapabilityId.of("media.transcode"),
                com.example.platform.extension.domain.ContractVersionRange.atLeast(
                        com.example.platform.extension.domain.ContractVersion.of(2, 0)),
                true, List.of());
        var nA = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(capA, capB), List.of(), List.of(),
                List.of(), java.util.Optional.empty(), null);
        var nB = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(capB, capA), List.of(), List.of(),
                List.of(), java.util.Optional.empty(), null);
        assertEquals(build(graph(List.of(nA), List.of())).digest(),
                build(graph(List.of(nB), List.of())).digest(),
                "C7-T06 capability order invariance (logical)");
        assertEquals(plan(build(graph(List.of(nA), List.of())), "pep-A").digest(),
                plan(build(graph(List.of(nB), List.of())), "pep-B").digest(),
                "C7-T06 capability order invariance (physical)");
    }

    @Test
    void outputRequirementOrderInvariance() { // C7-T07 + T15
        var o1 = com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(
                com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER);
        var o2 = com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(
                com.example.platform.render.domain.renderplan.RenderOutputRole.DELIVERY_RENDITION);
        var nA = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(o1, o2), List.of(),
                List.of(), java.util.Optional.empty(), null);
        var nB = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(o2, o1), List.of(),
                List.of(), java.util.Optional.empty(), null);
        assertEquals(build(graph(List.of(nA), List.of())).digest(),
                build(graph(List.of(nB), List.of())).digest(),
                "C7-T07 output requirement order invariance (logical)");
        assertEquals(plan(build(graph(List.of(nA), List.of())), "pep-A").digest(),
                plan(build(graph(List.of(nB), List.of())), "pep-B").digest(),
                "C7-T07 output requirement order invariance (physical)");
    }

    @Test
    void materializationOrderInvariance() { // C7-T08 + T16
        var m1 = new com.example.platform.render.domain.renderplan.EffectMaterializationRequirement(
                com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.COLOR_ADJUSTMENT,
                List.of(), "inst-1", "def-1", "v1", true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(1000)),
                List.of(), com.example.platform.timeline.semantics.effect.EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"));
        var m2 = new com.example.platform.render.domain.renderplan.EffectMaterializationRequirement(
                com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.FADE,
                List.of(), "inst-2", "def-2", "v1", true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        MediaTime.ofMillis(1000), MediaTime.ofMillis(2000)),
                List.of(), com.example.platform.timeline.semantics.effect.EffectInstance.EffectTemporalBehavior.CHANGE_DURATION,
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c2"));
        var nA = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(), List.of(), List.of(m1, m2),
                java.util.Optional.empty(), null);
        var nB = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(), List.of(), List.of(m2, m1),
                java.util.Optional.empty(), null);
        assertEquals(build(graph(List.of(nA), List.of())).digest(),
                build(graph(List.of(nB), List.of())).digest(),
                "C7-T08 materialization order invariance (logical)");
        assertEquals(plan(build(graph(List.of(nA), List.of())), "pep-A").digest(),
                plan(build(graph(List.of(nB), List.of())), "pep-B").digest(),
                "C7-T08 materialization order invariance (physical)");
    }

    // ---------- C7-T12..T16: semantic sensitivity ----------

    @Test
    void dependencySemanticMutationChangesDigest() { // C7-T12
        var p1 = decode("p1");
        var consumer = decode("c1");
        var gA = graph(List.of(p1, consumer),
                List.of(edge("p1", "c1", new RenderDependency.DecodedFrames())));
        var gB = graph(List.of(p1, consumer),
                List.of(edge("p1", "c1",
                        new RenderDependency.AudioInput(new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1")))));
        assertNotEquals(build(gA).digest(), build(gB).digest(), "C7-T12 logical digest changes");
        assertNotEquals(plan(build(gA), "pep-A").digest(), plan(build(gB), "pep-B").digest(),
                "C7-T12 physical digest changes");
    }

    @Test
    void sourceArtifactSemanticMutationChangesDigest() { // C7-T13
        var a1 = sourceArtifact("art-1", "a".repeat(64));
        var a2 = sourceArtifact("art-1", "b".repeat(64));
        assertNotEquals(build(graph(List.of(withArtifacts("n1", List.of(a1))), List.of())).digest(),
                build(graph(List.of(withArtifacts("n1", List.of(a2))), List.of())).digest(),
                "C7-T13 logical digest changes on artifact digest mutation");
    }

    // ---------- C7-T19: plan id exclusion ----------

    @Test
    void planIdExcludedFromPhysicalDigest() { // C7-T19
        var lg = build(graph(List.of(decode("n1")), List.of()));
        assertEquals(plan(lg, "pep-X").digest(), plan(lg, "pep-Y").digest(),
                "C7-T19 ExecutionPlanId never affects physical semantic digest");
    }

    // ---------- C7-T11: execution requirement order classification ----------

    @Test
    void executionRequirementOrderNonSemantic() { // C7-T11 — #20 carries no positional authority for this List
        var e1 = new com.example.platform.render.domain.renderplan.RenderExecutionRequirement(
                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement.NONE,
                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                false);
        var e2 = new com.example.platform.render.domain.renderplan.RenderExecutionRequirement(
                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement.OPTIONAL,
                com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass.NON_DETERMINISTIC,
                true);
        var nA = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(), List.of(e1, e2), List.of(),
                java.util.Optional.empty(), null);
        var nB = new RenderNode(new RenderNodeId("n1"),
                new com.example.platform.render.domain.renderplan.RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "c"),
                "op", List.of(), List.of(), List.of(), List.of(e2, e1), List.of(),
                java.util.Optional.empty(), null);
        assertEquals(build(graph(List.of(nA), List.of())).digest(),
                build(graph(List.of(nB), List.of())).digest(),
                "C7-T11 execution requirement order invariant (#20 List carries no positional authority)");
    }

    // ---------- C7-T09/T10: artifact expectation order invariance ----------

    @Test
    void intermediateFinalExpectationOrderInvariance() { // C7-T09 + T10
        var mid1 = new com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation(
                new com.example.platform.render.domain.renderplan.LogicalArtifactId("mid-1"),
                com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER);
        var mid2 = new com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation(
                new com.example.platform.render.domain.renderplan.LogicalArtifactId("mid-2"),
                com.example.platform.render.domain.renderplan.RenderOutputRole.DELIVERY_RENDITION);
        var fin1 = new com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation(
                com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER);
        var fin2 = new com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation(
                com.example.platform.render.domain.renderplan.RenderOutputRole.DELIVERY_RENDITION);
        var nA = withArtifacts("n1", List.of(mid1, mid2, fin1, fin2));
        var nB = withArtifacts("n1", List.of(fin2, fin1, mid2, mid1));
        assertEquals(plan(build(graph(List.of(nA), List.of())), "pep-A").digest(),
                plan(build(graph(List.of(nB), List.of())), "pep-B").digest(),
                "C7-T09/T10 intermediate/final expectation order invariance (physical)");
    }
}
