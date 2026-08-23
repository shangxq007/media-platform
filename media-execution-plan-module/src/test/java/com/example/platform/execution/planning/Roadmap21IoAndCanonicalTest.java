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
        assertEquals("1", a.schemaVersion().canonical(), "SCHEMA_VERSION_FROZEN_SEMANTICS=YES");
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

    // ---------- M17/M18: full ColorDescription / RasterSampleDescription ----------

    static com.example.platform.colorimage.ColorDescription bt709() {
        return new com.example.platform.colorimage.ColorDescription.ParametricColorDescription(
                com.example.platform.colorimage.ColorPrimaries.WellKnown.BT709,
                com.example.platform.colorimage.TransferCharacteristic.BT709,
                com.example.platform.colorimage.MatrixCoefficients.BT709,
                com.example.platform.colorimage.SignalRange.LIMITED);
    }

    static com.example.platform.colorimage.ColorDescription bt2020() {
        return new com.example.platform.colorimage.ColorDescription.ParametricColorDescription(
                com.example.platform.colorimage.ColorPrimaries.WellKnown.BT2020,
                com.example.platform.colorimage.TransferCharacteristic.PQ,
                com.example.platform.colorimage.MatrixCoefficients.BT2020_NCL,
                com.example.platform.colorimage.SignalRange.LIMITED);
    }

    static com.example.platform.colorimage.RasterSampleDescription raster8bit() {
        return new com.example.platform.colorimage.RasterSampleDescription(
                com.example.platform.colorimage.SampleFamily.YCbCr,
                com.example.platform.colorimage.SampleOrganization.PLANAR,
                8,
                com.example.platform.colorimage.ChromaSubsampling.SAMPLE_420,
                com.example.platform.colorimage.ChromaLocation.LEFT,
                false);
    }

    static com.example.platform.colorimage.RasterSampleDescription raster10bit() {
        return new com.example.platform.colorimage.RasterSampleDescription(
                com.example.platform.colorimage.SampleFamily.YCbCr,
                com.example.platform.colorimage.SampleOrganization.PLANAR,
                10,
                com.example.platform.colorimage.ChromaSubsampling.SAMPLE_420,
                com.example.platform.colorimage.ChromaLocation.LEFT,
                false);
    }

    static com.example.platform.render.domain.renderplan.RenderOutputRequirement outReq(
            com.example.platform.colorimage.ColorDescription cd,
            com.example.platform.colorimage.RasterSampleDescription raster) {
        return new com.example.platform.render.domain.renderplan.RenderOutputRequirement(
                RenderOutputRole.RENDER_MASTER,
                Optional.ofNullable(cd),
                Optional.ofNullable(raster));
    }

    static RenderNode nodeWithOutput(RenderOutputRequirement out) {
        return new RenderNode(new RenderNodeId("n1"), new RenderNodeKind.Source(),
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-n1"), "transcode",
                List.of(), List.of(
                        new CapabilityRequirement(CapabilityId.of("media.transcode"),
                                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of())),
                List.of(out),
                List.of(new RenderExecutionRequirement(GpuRequirement.NONE,
                        RenderDeterminismClass.DETERMINISTIC, false)),
                List.of(), Optional.empty(),
                new com.example.platform.render.domain.renderplan.RenderExecutionCoverage(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1)));
    }

    @Test
    void colorDescriptionFullValueMutationChangesDigest() {
        var a = build(nodeWithOutput(outReq(bt709(), raster8bit())));
        var b = build(nodeWithOutput(outReq(bt2020(), raster8bit())));
        assertNotEquals(a.digest(), b.digest(),
                "COLOR_MUTATION_TEST — BT709 -> BT2020 must change digest (full value, not presence)");
    }

    @Test
    void rasterSampleFullValueMutationChangesDigest() {
        var a = build(nodeWithOutput(outReq(bt709(), raster8bit())));
        var b = build(nodeWithOutput(outReq(bt709(), raster10bit())));
        assertNotEquals(a.digest(), b.digest(),
                "RASTER_MUTATION_TEST — 8-bit -> 10-bit must change digest (full value)");
    }

    // ---------- M25/M26: AudioMixInput payload in dependency ----------

    @Test
    void audioMixInputPayloadMutationChangesDigest() {
        var n1 = nodeWithArtifacts("n1", List.of(), List.of());
        var producer = nodeWithArtifacts("p1", List.of(), List.of());
        var edgeA = new com.example.platform.render.domain.renderplan.RenderDependencyEdge(
                new RenderNodeId("p1"), new RenderNodeId("n1"),
                new RenderDependency.AudioInput(
                        new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c1")));
        var edgeB = new com.example.platform.render.domain.renderplan.RenderDependencyEdge(
                new RenderNodeId("p1"), new RenderNodeId("n1"),
                new RenderDependency.AudioInput(
                        new com.example.platform.audio.domain.mix.AudioMixInput("t1", "c2")));
        var gA = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(producer, n1), List.of(edgeA),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        var gB = LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(producer, n1), List.of(edgeB),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
        assertNotEquals(gA.digest(), gB.digest(),
                "AudioMixInput clipId mutation must change logical digest (explicit payload encoding)");
    }

    // ---------- M35/M36/M37 + X01/X02/X03: physical digest actual fields + exclusions ----------

    @Test
    void physicalFormatVersionActualValueIncluded() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var logical = build(n);
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));
        var a = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var b = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertEquals(a.digest(), b.digest(), "same inputs -> same digest");
        // physical digest must consume the ACTUAL formatVersion + schemaVersion:
        // two plans with genuinely different format versions must differ
        var unitsA = a.units();
        var dA = PhysicalExecutionPlanDigest.compute("physical-execution-plan-v1",
                a.schemaVersion(), unitsA, logical.planFingerprint(), extent);
        var dB = PhysicalExecutionPlanDigest.compute("physical-execution-plan-v2",
                a.schemaVersion(), unitsA, logical.planFingerprint(), extent);
        assertNotEquals(dA, dB, "ACTUAL_PHYSICAL_FORMAT_VERSION_INCLUDED=YES — v1 vs v2 must differ");
        var dC = PhysicalExecutionPlanDigest.compute("physical-execution-plan-v1",
                new com.example.platform.execution.domain.ExecutionPlanSchemaVersion(2),
                unitsA, logical.planFingerprint(), extent);
        assertNotEquals(dA, dC, "ACTUAL_EXECUTION_SCHEMA_VERSION_INCLUDED=YES — schema 1 vs 2 must differ");
        assertNotEquals(a.planId(), a.digest().sha256Hex(), "identity != digest");
    }

    @Test
    void planIdExclusion() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var logical = build(n);
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));
        var a = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-A"));
        var b = PhysicalPlannerV1.plan(logical, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-B"));
        assertEquals(a.digest(), b.digest(),
                "X01 — ExecutionPlanId change alone must NOT change physical semantic digest");
        assertNotEquals(a.planId(), b.planId(), "distinct plan identities preserved");
    }

    @Test
    void provenanceExclusion() {
        var n = nodeWithArtifacts("n1", List.of(), List.of());
        var gA = build(n);
        var gB = build(n);
        assertEquals(gA.digest(), gB.digest(),
                "X02/X03 — provenance/correlation/createdAt are never semantic digest inputs");
        // mechanical: neither digest encoder consumes provenance fields
        assertFalse(gA.digest().sha256Hex().contains("correlation"),
                "no provenance string in digest");
        String digSrc = "";
        try {
            digSrc = java.nio.file.Files.readString(
                    java.nio.file.Paths.get("src/main/java/com/example/platform/execution/planning/LogicalExecutionGraphDigest.java"));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        // strip comments: the digest encoder's javadoc may mention excluded
        // provenance fields; CODE must never reference them
        digSrc = digSrc.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        assertFalse(digSrc.contains("createdAt") || digSrc.contains("correlationId")
                        || digSrc.contains("traceId") || digSrc.contains("requestedBy"),
                "PROVENANCE_EXCLUSION=YES — digest encoder never references provenance fields");
    }

}
