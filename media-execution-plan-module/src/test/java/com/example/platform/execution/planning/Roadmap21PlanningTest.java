package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanId;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap #21 end-to-end planning tests (contract evidence).
 */
class Roadmap21PlanningTest {

    static RenderNode node(String id, String opKey) {
        return new RenderNode(new RenderNodeId(id), new RenderNodeKind.Source(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.CLIP, "clip-1"), opKey,
                List.of(), List.of(
                        new CapabilityRequirement(CapabilityId.of("media.transcribe"),
                                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of())),
                List.of(), List.of(new RenderExecutionRequirement(GpuRequirement.NONE,
                        RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC, false)),
                List.of(), Optional.empty());
    }

    static RenderPlan plan(RenderNode... nodes) {
        var req = new RenderRequest(new RenderRequestId("req-1"),
                new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1)),
                List.of());
        var revision = new com.example.platform.render.domain.renderplan.TimelineRevisionReference(
                "rev-1",
                new com.example.platform.shared.digest.ContentDigest(
                        com.example.platform.shared.digest.ContentDigest.DigestAlgorithm.SHA_256, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        var effectRef = new com.example.platform.render.domain.renderplan.EffectSemanticReference(
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference(
                        com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId.of("snap-1"),
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion.of("v1")),
                "rev-1");
        var provenance = new com.example.platform.render.domain.renderplan.RenderPlanProvenance(
                "render-plan-v1", "rev-1", effectRef);
        return new RenderPlan(new RenderPlanId("plan-1"), "render-plan-v1", revision, effectRef, req,
                List.of(nodes), List.of(), new RenderPlanFingerprint("fp-1"), provenance);
    }

    static RenderGraph graph(String fp, RenderNode... nodes) {
        return new RenderGraph("render-graph-v1", new RenderPlanFingerprint(fp),
                List.of(nodes), List.of(), new RenderGraphFingerprint("gf-1"));
    }

    @Test
    void oneRenderNodeToOneLogicalNodeToOnePhysicalUnit() {
        var n1 = node("n1", "transcribe");
        var plan = plan(n1);
        var graph = graph("fp-1", n1);
        var result = LogicalPhysicalPlanner.plan(plan, graph);
        assertEquals(1, result.logicalExecutionGraph().nodes().size());
        assertEquals("n1", result.logicalExecutionGraph().nodes().get(0).sourceRenderNodeId().value());
        assertEquals(1, result.physicalExecutionPlan().units().size());
        assertEquals("n1", result.physicalExecutionPlan().units().get(0).sourceRenderNodeId().value());
    }

    @Test
    void exactRenderDependencyVariantsPreserved() {
        var n1 = node("n1", "decode");
        var n2 = node("n2", "effect");
        var plan = plan(n1, n2);
        var graph = new RenderGraph(
                "render-graph-v1",
                new RenderPlanFingerprint("fp-1"),
                List.of(n1, n2),
                List.of(new RenderDependencyEdge(
                        new RenderNodeId("n1"),
                        new RenderNodeId("n2"),
                        new RenderDependency.DecodedFrames())),
                new RenderGraphFingerprint("gf-1"));
        var result = LogicalPhysicalPlanner.plan(plan, graph);
        assertEquals(1, result.logicalExecutionGraph().edges().size());
        var edge = result.logicalExecutionGraph().edges().get(0);
        assertTrue(edge.dependencyVariant() instanceof RenderDependency.DecodedFrames,
                "exact RenderDependency variant must be preserved — no generic DATA authority");
    }

    @Test
    void cycleFailsClosedWithTypedReason() {
        var n1 = node("n1", "a");
        var n2 = node("n2", "b");
        var plan = plan(n1, n2);
        var graph = new RenderGraph("render-graph-v1", new RenderPlanFingerprint("fp-1"),
                List.of(n1, n2), List.of(
                        new RenderDependencyEdge(new RenderNodeId("n1"), new RenderNodeId("n2"),
                                new RenderDependency.DecodedFrames()),
                        new RenderDependencyEdge(new RenderNodeId("n2"), new RenderNodeId("n1"),
                                new RenderDependency.DecodedFrames())),
                new RenderGraphFingerprint("gf-1"));
        ExecutionPlanningException ex = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.plan(plan, graph));
        assertEquals(ExecutionPlanningFailureReason.CYCLE_DETECTED, ex.reason());
    }

    static com.example.platform.render.domain.renderplan.RenderSampleWindow sampleWindow() {
        return new com.example.platform.render.domain.renderplan.RenderSampleWindow(
                MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
    }

    @Test
    void sameInputsProduceSameDigests() {
        var n1 = node("n1", "transcribe");
        var plan = plan(n1);
        var graph = graph("fp-1", n1);
        var a = LogicalPhysicalPlanner.plan(plan, graph);
        var b = LogicalPhysicalPlanner.plan(plan, graph);
        assertEquals(a.logicalExecutionGraph().digest(), b.logicalExecutionGraph().digest());
        assertEquals(a.physicalExecutionPlan().digest(), b.physicalExecutionPlan().digest());
    }

    @Test
    void fingerprintMismatchFailsClosed() {
        var n1 = node("n1", "x");
        var plan = plan(n1);
        var graph = graph("fp-WRONG", n1);
        ExecutionPlanningException ex = assertThrows(ExecutionPlanningException.class,
                () -> LogicalPhysicalPlanner.plan(plan, graph));
        assertEquals(ExecutionPlanningFailureReason.UNSATISFIED_STRUCTURAL_CONSTRAINT, ex.reason());
    }

    @Test
    void capabilityPropagatesWithoutInventionOrDowngrade() {
        var n1 = node("n1", "transcribe");
        var plan = plan(n1);
        var graph = graph("fp-1", n1);
        var result = LogicalPhysicalPlanner.plan(plan, graph);
        var er = result.executionRequirement();
        assertEquals(1, er.capabilityRequirementRefs().size());
        assertEquals(CapabilityId.of("media.transcribe"), er.capabilityRequirementRefs().get(0).capabilityId());
        assertNotNull(er.capabilityRequirementRefs().get(0).contractRange());
    }

    @Test
    void physicalUnitsCarryNoProviderOrDeviceBinding() {
        var n1 = node("n1", "x");
        var plan = plan(n1);
        var graph = graph("fp-1", n1);
        var result = LogicalPhysicalPlanner.plan(plan, graph);
        var unit = result.physicalExecutionPlan().units().get(0);
        assertFalse(unit.toString().toLowerCase().contains("provider"));
        assertFalse(unit.toString().toLowerCase().contains("worker"));
        assertFalse(unit.toString().toLowerCase().contains("gpu"));
    }
}
