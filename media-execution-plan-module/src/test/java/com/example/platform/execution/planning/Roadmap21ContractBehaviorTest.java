package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderComponentKind;
import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanProvenance;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.render.domain.renderplan.TimelineRevisionReference;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.digest.ContentDigest.DigestAlgorithm;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 frozen C24 behavioral acceptance matrix (Blocker A-K evidence).
 */
class Roadmap21ContractBehaviorTest {

    // ---------- fixtures ----------

    static RenderSampleWindow window(long startMs, long endMs) {
        return new RenderSampleWindow(
                MediaTime.ofMillis(startMs), MediaTime.ofMillis(endMs), FrameRate.of(25, 1));
    }

    static RenderNode node(String id, String opKey, RenderNodeKind kind, RenderSampleWindow w) {
        return new RenderNode(new RenderNodeId(id), kind,
                RenderComponentPath.of(RenderComponentKind.CLIP, "clip-" + id), opKey,
                List.of(), List.of(
                        new CapabilityRequirement(CapabilityId.of("media." + opKey),
                                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of())),
                List.of(RenderOutputRequirement.of(
                        com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER)),
                List.of(new RenderExecutionRequirement(GpuRequirement.NONE, RenderDeterminismClass.DETERMINISTIC, false)),
                List.of(), // no materialization declared on this fixture node (typed empty list)
                w != null ? Optional.of(w) : Optional.empty());
    }

    static RenderNode node(String id, String opKey) {
        return node(id, opKey, new RenderNodeKind.Source(), null);
    }

    static RenderPlan plan(RenderNode... nodes) {
        return plan(new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(100000), FrameRate.of(25, 1)), nodes);
    }

    static RenderPlan plan(RenderExtent extent, RenderNode... nodes) {
        var req = new RenderRequest(new RenderRequestId("req-1"), extent, List.of());
        var revision = new TimelineRevisionReference("rev-1",
                new ContentDigest(DigestAlgorithm.SHA_256,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        var effectRef = new com.example.platform.render.domain.renderplan.EffectSemanticReference(
                new EffectSemanticSnapshotReference(
                        EffectSemanticSnapshotId.of("snap-1"),
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        EffectSemanticContractVersion.of("v1")),
                "rev-1");
        var provenance = new RenderPlanProvenance("render-plan-v1", "rev-1", effectRef);
        return new RenderPlan(new RenderPlanId("plan-1"), "render-plan-v1", revision, effectRef, req,
                List.of(nodes), List.of(), new RenderPlanFingerprint("fp-1"), provenance);
    }

    static RenderGraph graph(String fp, List<RenderNode> nodes, List<RenderDependencyEdge> edges) {
        return new RenderGraph("render-graph-v1", new RenderPlanFingerprint(fp),
                nodes, edges, new RenderGraphFingerprint("gf-1"));
    }

    static RenderDependencyEdge edge(String p, String c, RenderDependency dep) {
        return new RenderDependencyEdge(new RenderNodeId(p), new RenderNodeId(c), dep);
    }

    // ---------- C24 matrix ----------

    @Test
    void sameInputDeterminism() {
        var n = node("n1", "transcode");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var a = LogicalPhysicalPlanner.plan(p, g);
        var b = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(a.executionRequirement(), b.executionRequirement());
        assertEquals(a.logicalExecutionGraph(), b.logicalExecutionGraph());
        assertEquals(a.logicalExecutionGraph().digest(), b.logicalExecutionGraph().digest());
        assertEquals(a.physicalExecutionPlan().digest(), b.physicalExecutionPlan().digest());
    }

    @Test
    void invalidInputFailsClosed() {
        assertThrows(NullPointerException.class, () -> LogicalPhysicalPlanner.plan(null, null));
    }

    @Test
    void cycleFailsClosedTyped() {
        var n1 = node("n1", "a");
        var n2 = node("n2", "b");
        var p = plan(n1, n2);
        var g = graph("fp-1", List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames()),
                        edge("n2", "n1", new RenderDependency.DecodedFrames())));
        ExecutionPlanningException ex = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.plan(p, g));
        assertEquals(ExecutionPlanningFailureReason.CYCLE_DETECTED, ex.reason());
        assertInstanceOf(ExecutionPlanningException.CycleContext.class, ex.context());
        assertFalse(((ExecutionPlanningException.CycleContext) ex.context()).cycleNodeIds().isEmpty());
    }

    @Test
    void extentPrunesOutsideRequestedRange() {
        // node n1 window [0,10s] inside extent [0,10s]; node n2 window [20s,30s] disjoint -> pruned
        var n1 = node("n1", "transcode", new RenderNodeKind.Source(), window(0, 10000));
        var n2 = node("n2", "outro", new RenderNodeKind.Source(), window(20000, 30000));
        var extent = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var p = plan(extent, n1, n2);
        var g = graph("fp-1", List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(1, result.logicalExecutionGraph().nodes().size());
        assertEquals("n1", result.logicalExecutionGraph().nodes().get(0).sourceRenderNodeId().value());
        var evidence = result.logicalExecutionGraph().pruningEvidence();
        assertNotNull(evidence);
        assertTrue(evidence.pruningApplied());
        assertEquals(1, evidence.eliminatedNodes().size());
        assertEquals("n2", evidence.eliminatedNodes().get(0).sourceRenderNodeId().value());
        assertEquals("DISJOINT_WINDOW", evidence.eliminatedNodes().get(0).reason());
    }

    @Test
    void extentBoundaryIsExact() {
        // window ending exactly AT extent.start is disjoint (w.end <= extent.start)
        var n1 = node("n1", "tail", new RenderNodeKind.Source(), window(0, 5000));
        var extent = new RenderExtent(MediaTime.ofMillis(5000), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var p = plan(extent, n1);
        var g = graph("fp-1", List.of(n1), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertTrue(result.logicalExecutionGraph().pruningEvidence().pruningApplied());
        assertEquals(0, result.logicalExecutionGraph().nodes().size(),
                "window.end == extent.start is provably outside -> pruned");
    }

    @Test
    void extentPruningIsDeterministic() {
        var n1 = node("n1", "a", new RenderNodeKind.Source(), window(0, 10000));
        var n2 = node("n2", "b", new RenderNodeKind.Source(), window(20000, 30000));
        var extent = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var p = plan(extent, n1, n2);
        var g = graph("fp-1", List.of(n1, n2), List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var a = LogicalPhysicalPlanner.plan(p, g);
        var b = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(a.logicalExecutionGraph().digest(), b.logicalExecutionGraph().digest());
        assertEquals(a.logicalExecutionGraph().pruningEvidence(), b.logicalExecutionGraph().pruningEvidence());
    }

    @Test
    void extentPruningCannotRemoveContributingWork() {
        // n2 window [8s,12s] overlaps extent [0,10s] -> NOT pruned (contributing)
        var n1 = node("n1", "a", new RenderNodeKind.Source(), window(0, 10000));
        var n2 = node("n2", "b", new RenderNodeKind.Source(), window(8000, 12000));
        var extent = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var p = plan(extent, n1, n2);
        var g = graph("fp-1", List.of(n1, n2), List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(2, result.logicalExecutionGraph().nodes().size(),
                "overlapping window must NOT be pruned");
    }

    @Test
    void parallelIndependencePreserved() {
        // two branches with no cross edges -> both nodes independent (no edge between them)
        var n1 = node("n1", "a");
        var n2 = node("n2", "b");
        var p = plan(n1, n2);
        var g = graph("fp-1", List.of(n1, n2), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(0, result.logicalExecutionGraph().edges().size(),
                "no invented edge -> branches structurally independent");
    }

    @Test
    void temporalExactnessPreservedEndToEnd() {
        var w = window(1000, 9000);
        var n = node("n1", "transcode", new RenderNodeKind.Source(), w);
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(w, result.logicalExecutionGraph().nodes().get(0).requiredSampleWindow());
        assertEquals(w, result.physicalExecutionPlan().units().get(0).temporalWindow());
        // exact rational, canonical reduced form: 9000ms == 9/1 s (MediaTime
        // reduces ticks/scale by gcd — still exactly 9000ms, never float)
        assertEquals(9L, w.end().ticks());
        assertEquals(1L, w.end().timeScale());
        assertEquals(9L * 1000L, w.end().ticks() * w.end().timeScale() * 1000L / w.end().timeScale() / 1000L * 1000L);
    }

    @Test
    void partitionPreservationOneToOne() {
        var n1 = node("n1", "a");
        var n2 = node("n2", "b");
        var p = plan(n1, n2);
        var g = graph("fp-1", List.of(n1, n2), List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var result = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(2, result.physicalExecutionPlan().units().size());
        for (var u : result.physicalExecutionPlan().units()) {
            assertNotNull(u.sourceRenderNodeKind());
            assertTrue(u.typedInputs() != null && u.typedOutputs() != null);
        }
    }

    @Test
    void capabilityPropagationWithoutInventionOrDowngrade() {
        var n = node("n1", "transcribe");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        var er = result.executionRequirement();
        assertEquals(1, er.capabilityRequirementRefs().size());
        assertEquals(CapabilityId.of("media.transcribe"),
                er.capabilityRequirementRefs().get(0).declaration().capabilityId());
        assertNotNull(er.capabilityRequirementRefs().get(0).declaration().contractRange(),
                "full CapabilityRequirement semantics — no CapabilityId-only downgrade");
        assertEquals(1, result.physicalExecutionPlan().units().get(0).capabilityRequirementRefs().size());
    }

    @Test
    void runtimeStateInvariance() {
        var n = node("n1", "transcode");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var a = LogicalPhysicalPlanner.plan(p, g);
        var b = LogicalPhysicalPlanner.plan(p, g);
        assertEquals(a.physicalExecutionPlan().digest(), b.physicalExecutionPlan().digest());
        // no runtime registry/clock reads possible: planner is pure static
    }

    @Test
    void planFingerprintConsistencyFailsClosed() {
        var n = node("n1", "a");
        var p = plan(n);
        var g = graph("fp-WRONG", List.of(n), List.of());
        ExecutionPlanningException ex = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.plan(p, g));
        assertEquals(ExecutionPlanningFailureReason.UNSATISFIED_STRUCTURAL_CONSTRAINT, ex.reason());
        assertInstanceOf(ExecutionPlanningException.FingerprintMismatchContext.class, ex.context());
    }

    @Test
    void dependencyVariantPreservationExactPayload() {
        var n1 = node("n1", "decode", new RenderNodeKind.Source(), window(0, 10000));
        var n2 = node("n2", "effect", new RenderNodeKind.Effect(), window(0, 10000));
        var p = plan(n1, n2);
        var g = graph("fp-1", List.of(n1, n2),
                List.of(edge("n1", "n2", new RenderDependency.DecodedFrames())));
        var result = LogicalPhysicalPlanner.plan(p, g);
        var le = result.logicalExecutionGraph().edges().get(0);
        assertInstanceOf(RenderDependency.DecodedFrames.class, le.dependencyVariant());
        // physical digest includes the exact variant payload
        assertNotNull(result.physicalExecutionPlan().units().get(1).typedInputs().get(0).dependencyVariant());
        assertInstanceOf(RenderDependency.DecodedFrames.class,
                result.physicalExecutionPlan().units().get(1).typedInputs().get(0).dependencyVariant());
    }

    @Test
    void typedIoPropagationAndOutputMaterialization() {
        var n = node("n1", "transcode");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        var unit = result.physicalExecutionPlan().units().get(0);
        assertEquals(1, unit.typedOutputs().size());
        assertEquals(1, unit.typedOutputs().get(0).outputRequirements().size());
        assertEquals(com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER,
                unit.typedOutputs().get(0).outputRequirements().get(0).role(),
                "typed output requirement propagated — not string keys");
        assertNotNull(unit.typedOutputs().get(0).materializationRequirements(),
                "typed materialization list propagated (may be empty for nodes without declarations)");
    }

    @Test
    void danglingReferenceRejected() {
        // edge references node that does not exist -> builder fail-closed
        var n1 = node("n1", "a");
        var p = plan(n1);
        var g = graph("fp-1", List.of(n1),
                List.of(edge("n1", "ghost", new RenderDependency.DecodedFrames())));
        ExecutionPlanningException ex = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.plan(p, g));
        assertEquals(ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH, ex.reason());
        assertInstanceOf(ExecutionPlanningException.MissingReferenceContext.class, ex.context());
    }

    @Test
    void duplicateSourceIdentityRejected() {
        var n1 = node("n1", "a");
        var p = plan(n1);
        var g = graph("fp-1", List.of(n1, n1), List.of());
        assertThrows(ExecutionPlanningException.class, () -> LogicalPhysicalPlanner.plan(p, g));
    }

    @Test
    void physicalUnitsCarryNoProviderOrWorkerOrDeviceBinding() {
        var n = node("n1", "transcode");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        var unit = result.physicalExecutionPlan().units().get(0);
        // no #22 binding vocabulary anywhere in the typed plan unit
        assertFalse(unit.toString().toLowerCase().contains("provider"));
        assertFalse(unit.toString().toLowerCase().contains("worker"));
        assertFalse(unit.toString().toLowerCase().contains("device"));
        assertFalse(unit.toString().toLowerCase().contains("queue"));
        assertFalse(unit.toString().toLowerCase().contains("availability"));
        // gpu appears ONLY as the propagated #20 declaration value NONE
        // (RenderExecutionRequirement.gpu is the #20 authority) — never a
        // #21 selection/binding
        if (unit.toString().contains("gpu=")) {
            assertTrue(unit.toString().contains("gpu=NONE"),
                    "gpu must only appear as propagated NONE declaration, never a binding");
        }
    }

    @Test
    void planIdentityDistinctFromSemanticDigest() {
        var n = node("n1", "transcode");
        var p = plan(n);
        var g = graph("fp-1", List.of(n), List.of());
        var result = LogicalPhysicalPlanner.plan(p, g);
        var pep = result.physicalExecutionPlan();
        assertNotNull(pep.planId());
        assertNotEquals(pep.planId().value(), pep.digest().sha256Hex(),
                "ExecutionPlanId is identity, NOT semantic digest");
        assertNotNull(pep.schemaVersion());
        assertEquals("1.0", pep.schemaVersion().canonical());
    }
}
