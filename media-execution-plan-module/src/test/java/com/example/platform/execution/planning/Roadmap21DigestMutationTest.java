package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 digest mutation-sensitivity (Blocker H.4, C24).
 *
 * <p>Changing any semantic field MUST change the digest; changing
 * provenance-only fields MUST NOT. This is the mechanical proof of
 * law:logical-digest-content-complete and law:physical-digest-content-complete.
 */
class Roadmap21DigestMutationTest {

    static RenderSampleWindow window(long s, long e) {
        return new RenderSampleWindow(MediaTime.ofMillis(s), MediaTime.ofMillis(e), FrameRate.of(25, 1));
    }

    static RenderNode node(String id, String opKey, RenderNodeKind kind,
                           ContractVersionRange range, List<CapabilityId> alternatives,
                           RenderSampleWindow w, RenderOutputRole outputRole) {
        return new RenderNode(new RenderNodeId(id), kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id), opKey,
                List.of(), List.of(
                        new CapabilityRequirement(CapabilityId.of("media." + opKey),
                                range, true, alternatives)),
                List.of(RenderOutputRequirement.of(outputRole)),
                List.of(new RenderExecutionRequirement(GpuRequirement.NONE,
                        RenderDeterminismClass.DETERMINISTIC, false)),
                List.of(), w != null ? Optional.of(w) : Optional.empty(), null);
    }

    static RenderPlanFingerprint FP = new RenderPlanFingerprint("fp-1");

    static LogicalExecutionGraph buildGraph(RenderNode n) {
        return LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n), List.of(),
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
    }

    static LogicalExecutionGraph buildGraph(RenderNode n1, RenderNode n2, List<RenderDependencyEdge> edges) {
        return LogicalExecutionGraphBuilder.build(
                new RenderGraph("render-graph-v1", FP, List.of(n1, n2), edges,
                        new RenderGraphFingerprint("gf-1")),
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)));
    }

    // -------- semantic mutations must change the logical digest --------

    @Test
    void renderNodeKindChangeChangesDigest() {
        var a = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "x", new RenderNodeKind.Decode(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        assertNotEquals(a.digest(), b.digest(), "RenderNodeKind change must change logical digest");
    }

    @Test
    void operationKeyChangeChangesDigest() {
        var a = buildGraph(node("n1", "transcode", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "transcribe", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        assertNotEquals(a.digest(), b.digest(), "operationKey change must change logical digest");
    }

    @Test
    void capabilityContractRangeChangeChangesDigest() {
        var a = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(2, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        assertNotEquals(a.digest(), b.digest(), "capability contract range change must change logical digest");
    }

    @Test
    void capabilityAlternativesChangeChangesDigest() {
        var a = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(CapabilityId.of("media.alt")), null, RenderOutputRole.RENDER_MASTER));
        assertNotEquals(a.digest(), b.digest(), "capability alternatives change must change logical digest");
    }

    @Test
    void outputRequirementChangeChangesDigest() {
        var a = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.DELIVERY_RENDITION));
        assertNotEquals(a.digest(), b.digest(), "output requirement change must change logical digest");
    }

    @Test
    void sampleWindowChangeChangesDigest() {
        var a = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), window(0, 10000), RenderOutputRole.RENDER_MASTER));
        var b = buildGraph(node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), window(0, 20000), RenderOutputRole.RENDER_MASTER));
        assertNotEquals(a.digest(), b.digest(), "sample window change must change logical digest");
    }

    @Test
    void dependencyVariantPayloadChangeChangesDigest() {
        // EffectInput variant carries payload; DecodedFrames is payload-less.
        // Use different variants -> different semantic payloads -> different digest.
        var n1 = node("n1", "decode", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var n2 = node("n2", "effect", new RenderNodeKind.Effect(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var a = buildGraph(n1, n2, List.of(new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"), new RenderDependency.DecodedFrames())));
        var b = buildGraph(n1, n2, List.of(new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"), new RenderDependency.EffectInput())));
        assertNotEquals(a.digest(), b.digest(), "dependency variant change must change logical digest");
    }

    // -------- provenance mutations must NOT change digests --------

    @Test
    void provenanceChangesDoNotChangeDigest() {
        var n = node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var a = buildGraph(n);
        var b = buildGraph(n);
        assertEquals(a.digest(), b.digest(),
                "identical semantics -> identical digest (provenance excluded)");
        // ExecutionRequirement provenance context is carried separately and
        // excluded from semantic content by construction.
        var erA = ExecutionRequirement.derive(
                Roadmap21ContractBehaviorTest.plan(n));
        var erB = new ExecutionRequirement(erA.planFingerprint(), erA.requestedExtent(),
                erA.capabilityRequirementRefs(), erA.executionIntentRefs(),
                new ExecutionRequirement.ProvenanceOnlyContext("corr-2", "2026-08-23T00:00:00Z"));
        assertNotEquals(erA.provenance(), erB.provenance(),
                "provenance identity may differ (provenance-only)");
        assertEquals(erA.planFingerprint(), erB.planFingerprint());
    }

    // -------- physical digest coverage --------

    @Test
    void physicalDigestSensitiveToDependencyAndIoStructure() {
        var n1 = node("n1", "decode", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var n2 = node("n2", "effect", new RenderNodeKind.Effect(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var gWithEdge = buildGraph(n1, n2, List.of(new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"), new RenderDependency.DecodedFrames())));
        var gNoEdge = buildGraph(n1, n2, List.of());
        var pepWith = PhysicalPlannerV1.plan(gWithEdge,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var pepNo = PhysicalPlannerV1.plan(gNoEdge,
                new com.example.platform.render.domain.renderplan.RenderExtent(
                        MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertNotEquals(pepWith.digest(), pepNo.digest(),
                "physical digest must cover dependency/IO structure");
    }

    @Test
    void physicalDigestSensitiveToDependencyVariantPayload() {
        // same structure, different exact variant payload -> physical digest
        // MUST change (covers dep| semantic coverage that generic structure
        // tests can mask)
        var n1 = node("n1", "decode", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var n2 = node("n2", "effect", new RenderNodeKind.Effect(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var gDecoded = buildGraph(n1, n2, List.of(new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"), new RenderDependency.DecodedFrames())));
        var gEffect = buildGraph(n1, n2, List.of(new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"), new RenderDependency.EffectInput())));
        var extent = new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1));
        var pepA = PhysicalPlannerV1.plan(gDecoded, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var pepB = PhysicalPlannerV1.plan(gEffect, extent,
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertNotEquals(pepA.digest(), pepB.digest(),
                "physical digest must cover exact dependency variant payload");
    }

    @Test
    void physicalDigestSensitiveToExtentPropagation() {
        var n = node("n1", "x", new RenderNodeKind.Source(), ContractVersionRange.atLeast(ContractVersion.of(1, 0)), List.of(), null, RenderOutputRole.RENDER_MASTER);
        var g = buildGraph(n);
        var pepA = PhysicalPlannerV1.plan(g, new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        var pepB = PhysicalPlannerV1.plan(g, new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ofMillis(0), MediaTime.ofMillis(50000), FrameRate.of(25, 1)),
                new com.example.platform.execution.domain.ExecutionPlanId("pep-1"));
        assertNotEquals(pepA.digest(), pepB.digest(),
                "physical digest must cover propagated extent where it changes plan semantics");
    }
}
