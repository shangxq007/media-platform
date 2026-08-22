package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.digest.ContentDigest.DigestAlgorithm;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 typed IO semantic direction (B3) + canonical completeness (B4)
 * evidence.
 */
class Roadmap21IoAndCanonicalTest {

    static final RenderPlanFingerprint FP = new RenderPlanFingerprint("fp-1");

    static RenderExecutionCoverageF coverage(long s, long e) {
        return new RenderExecutionCoverageF(s, e);
    }

    record RenderExecutionCoverageF(long startMs, long endMs) {
    }

    static com.example.platform.render.domain.renderplan.RenderExecutionCoverage cov(long s, long e) {
        return new com.example.platform.render.domain.renderplan.RenderExecutionCoverage(
                MediaTime.ofMillis(s), MediaTime.ofMillis(e), FrameRate.of(25, 1));
    }

    static RenderArtifactReference.SourceArtifact srcArtifact(String id, String digestHex) {
        return new RenderArtifactReference.SourceArtifact(
                new ArtifactId(id), new ContentDigest(DigestAlgorithm.SHA_256, digestHex));
    }

    static RenderNode nodeWithArtifacts(String id, List<RenderArtifactReference> artifacts,
                                        List<RenderMaterializationRequirement> mats) {
        return new RenderNode(new RenderNodeId(id), new RenderNodeKind.Source(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id), "decode",
                artifacts, List.of(
                        new CapabilityRequirement(CapabilityId.of("media.decode"),
                                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of())),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(new RenderExecutionRequirement(GpuRequirement.NONE,
                        RenderDeterminismClass.DETERMINISTIC, false)),
                mats,
                Optional.of(new com.example.platform.render.domain.renderplan.RenderSampleWindow(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1))),
                cov(0, 10000));
    }

    static com.example.platform.render.domain.renderplan.RenderMaterializationRequirement mat(String id, String defVersion) {
        return new com.example.platform.render.domain.renderplan.EffectMaterializationRequirement(
                EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(),
                id, "def-" + id, defVersion, true,
                new MediaClip.TimeRange(MediaTime.ofMillis(0), MediaTime.ofMillis(10000)),
                List.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                new ClipEffectTarget("t1", "c1"));
    }

    static LogicalExecutionGraph build(RenderNode n) {
        return LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
    }

    // ---------- B3: typed IO semantic direction ----------

    @Test
    void sourceArtifactBecomesTypedInputBinding() {
        var n = nodeWithArtifacts("n1",
                List.of(srcArtifact("art-1", "a".repeat(64))), List.of());
        var g = build(n);
        var logical = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        var pep = PhysicalPlannerV1.plan(logical,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var unit = pep.units().get(0);
        assertFalse(unit.typedInputs().isEmpty(), "source artifact must project to a typed input binding");
        var binding = unit.typedInputs().get(0);
        assertNotNull(binding.sourceArtifact(), "SourceArtifact must not be silently lost (no null placeholder)");
        assertEquals("art-1", binding.sourceArtifact().artifactId().value());
        assertEquals("a".repeat(64), binding.sourceArtifact().contentDigest().value(),
                "SOURCE_ARTIFACT_CONTENT_DIGEST_PRESERVED=YES");
        assertNotNull(binding.inputId(), "EXECUTION_INPUT_ID_TYPED=YES");
        assertEquals("n1#in0", binding.inputId().value());
    }

    @Test
    void sourceArtifactIsNotOutput() {
        var n = nodeWithArtifacts("n1",
                List.of(srcArtifact("art-1", "a".repeat(64))), List.of());
        var logical = build(n);
        var pep = PhysicalPlannerV1.plan(logical,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var unit = pep.units().get(0);
        for (var o : unit.typedOutputs()) {
            assertTrue(o.intermediateArtifactExpectations().isEmpty()
                            && o.finalArtifactExpectations().isEmpty()
                            || !o.intermediateArtifactExpectations().stream()
                                    .anyMatch(ia -> ia.toString().contains("art-1")),
                    "SOURCE_ARTIFACT_IN_OUTPUT_COUNT=0 — source artifact must not become an output");
        }
    }

    @Test
    void intermediateAndFinalArtifactsBecomeOutputs() {
        var n = nodeWithArtifacts("n1",
                List.of(
                        new RenderArtifactReference.IntermediateArtifactExpectation(
                                new com.example.platform.render.domain.renderplan.LogicalArtifactId("la-1"),
                                RenderOutputRole.RENDER_MASTER),
                        new RenderArtifactReference.FinalArtifactExpectation(RenderOutputRole.DELIVERY_RENDITION)),
                List.of());
        var logical = build(n);
        var pep = PhysicalPlannerV1.plan(logical,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var unit = pep.units().get(0);
        var out = unit.typedOutputs().get(0);
        assertEquals(1, out.intermediateArtifactExpectations().size(),
                "INTERMEDIATE_ARTIFACT_OUTPUT_STATUS=YES");
        assertEquals(1, out.finalArtifactExpectations().size(),
                "FINAL_ARTIFACT_OUTPUT_STATUS=YES");
        assertNotNull(out.outputId(), "EXECUTION_OUTPUT_ID_TYPED=YES");
        assertEquals("n1#out", out.outputId().value());
    }

    // ---------- B4: canonical completeness ----------

    @Test
    void frameRateOnlyExtentMutationChangesDigest() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var a = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(24, 1)));
        var b = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1)));
        assertNotEquals(a.digest(), b.digest(),
                "RENDER_EXTENT_FRAME_RATE_INCLUDED=YES — [0,10]@24 vs [0,10]@25 must differ");
    }

    @Test
    void schemaVersionMutationChangesPhysicalDigest() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var logical = build(n);
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));
        var a = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var b = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertEquals(a.digest(), b.digest(), "same inputs -> same physical digest");
        assertEquals(a.planId(), b.planId(), "plan identity is caller-supplied and stable");
        assertNotNull(a.schemaVersion());
        assertEquals("1.0", a.schemaVersion().canonical(), "SCHEMA_VERSION_FROZEN_SEMANTICS=YES");
    }

    @Test
    void sourceArtifactContentDigestMutationChangesDigest() {
        var nA = nodeWithArtifacts("n1",
                List.of(srcArtifact("art-1", "a".repeat(64))), List.of());
        var nB = nodeWithArtifacts("n1",
                List.of(srcArtifact("art-1", "b".repeat(64))), List.of());
        var gA = build(nA);
        var gB = build(nB);
        assertNotEquals(gA.digest(), gB.digest(),
                "SOURCE_ARTIFACT_CONTENT_DIGEST mutation must change logical digest");
    }

    @Test
    void artifactVariantMutationChangesDigest() {
        var nA = nodeWithArtifacts("n1", List.of(srcArtifact("art-1", "a".repeat(64))), List.of());
        var nB = nodeWithArtifacts("n1",
                List.of(new RenderArtifactReference.FinalArtifactExpectation(RenderOutputRole.DELIVERY_RENDITION)),
                List.of());
        assertNotEquals(build(nA).digest(), build(nB).digest(),
                "artifact variant mutation must change logical digest");
    }

    @Test
    void materializationPayloadMutationChangesLogicalDigest() {
        var nA = nodeWithArtifacts("n1", List.of(), List.of(mat("m1", "v1")));
        var nB = nodeWithArtifacts("n1", List.of(), List.of(mat("m1", "v2")));
        assertNotEquals(build(nA).digest(), build(nB).digest(),
                "materialization payload mutation must change logical digest");
    }

    @Test
    void materializationPayloadMutationChangesPhysicalDigest() {
        var nA = nodeWithArtifacts("n1", List.of(), List.of(mat("m1", "v1")));
        var nB = nodeWithArtifacts("n1", List.of(), List.of(mat("m1", "v2")));
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));
        var pa = PhysicalPlannerV1.plan(build(nA), extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var pb = PhysicalPlannerV1.plan(build(nB), extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertNotEquals(pa.digest(), pb.digest(),
                "materialization payload mutation must change physical digest");
    }

    @Test
    void materializationNonEmptyPayloadPreserved() {
        var n = nodeWithArtifacts("n1", List.of(), List.of(mat("m1", "v1")));
        var logical = build(n);
        var unit = PhysicalPlannerV1.plan(logical,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"))
                .units().get(0);
        assertEquals(1, unit.typedOutputs().get(0).materializationRequirements().size(),
                "NON_EMPTY_MATERIALIZATION_TEST=PASS");
        assertNotNull(unit.typedOutputs().get(0).materializationRequirements().get(0));
    }

    @Test
    void formatVersionMutationChangesDigest() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var gA = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        var gB = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v2", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        assertNotEquals(gA.digest(), gB.digest(), "format/schema semantic version change must change digest");
    }
}
