package com.example.platform.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderGraphFingerprint;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanProvenance;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.EffectSemanticReference;
import com.example.platform.render.domain.renderplan.TimelineRevisionReference;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.digest.ContentDigest.DigestAlgorithm;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link PhysicalPlanningEntryService} — the typed-chain #21
 * production entry. Verifies the PLANNABLE gate, delegation to the guarded #21
 * entry, plan-identity preservation, deterministic output, and the admission
 * projection.
 */
class PhysicalPlanningEntryServiceTest {

    private final PhysicalPlanningEntryService service = new PhysicalPlanningEntryService();

    // ---------- fixtures ----------

    private static RenderPlan plan() {
        RenderRequest request = new RenderRequest(
                new RenderRequestId("req-ppes"),
                new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10_000), FrameRate.of(25, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
        TimelineRevisionReference revision = new TimelineRevisionReference("rev-1",
                new ContentDigest(DigestAlgorithm.SHA_256, "a".repeat(64)));
        EffectSemanticReference effectRef = new EffectSemanticReference(
                new EffectSemanticSnapshotReference(
                        EffectSemanticSnapshotId.of("snap-1"),
                        "b".repeat(64),
                        EffectSemanticContractVersion.of("v1")),
                "rev-1");
        return new RenderPlan(new RenderPlanId("plan-1"), "render-plan-v1", revision, effectRef, request,
                List.of(node("n1", "decode")), List.of(),
                new RenderPlanFingerprint("fp-1"),
                new RenderPlanProvenance("render-plan-v1", "rev-1", effectRef));
    }

    private static RenderNode node(String id, String operationKey) {
        return new RenderNode(
                new RenderNodeId(id),
                new RenderNodeKind.Output(),
                com.example.platform.render.domain.renderplan.RenderComponentPath.of(
                        com.example.platform.render.domain.renderplan.RenderComponentKind.OUTPUT, "master"),
                operationKey,
                List.of(),
                List.of(),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                null);
    }

    private static RenderGraph graph() {
        return new RenderGraph("render-graph-v1", new RenderPlanFingerprint("fp-1"),
                List.of(node("n1", "decode")), List.of(), new RenderGraphFingerprint("gf-1"));
    }

    private static RenderPlanningResult result(RenderPlanStatus status) {
        return new RenderPlanningResult(plan(), graph(), status, List.of());
    }

    private static PhysicalPlanningEntryService.AdmissionIdentity identity() {
        return new PhysicalPlanningEntryService.AdmissionIdentity(
                new PlatformExecutionPlan.Scope("tenant-1", "workspace-1", "actor-1"),
                "idem-ppes-1",
                "hash-ppes-1",
                new PlatformExecutionPlan.EntitlementQuotaSnapshot("quota-1", Map.of(), 10L),
                new PlatformExecutionPlan.CorrelationAuditIdentity("corr-1", "audit-1"));
    }

    // ---------- tests ----------

    @Test
    void planProducesLogicalGraphAndPhysicalPlanForPlannableRenderResult() {
        ExecutionPlanningEntry.PlanningResult planned =
                service.plan(result(RenderPlanStatus.PLANNABLE), new ExecutionPlanId("pep-ppes-1"));

        assertNotNull(planned.executionRequirement(), "execution requirement produced");
        assertNotNull(planned.logicalExecutionGraph(), "logical execution graph produced");
        assertNotNull(planned.physicalExecutionPlan(), "physical execution plan produced");
        assertEquals(1, planned.logicalExecutionGraph().nodes().size(),
                "one render node projects to exactly one logical node");
        assertEquals(1, planned.physicalExecutionPlan().units().size(),
                "one logical node projects to exactly one physical plan unit");
    }

    @Test
    void planPreservesCallerSuppliedPlanIdentity() {
        ExecutionPlanId planId = new ExecutionPlanId("pep-ppes-identity");
        ExecutionPlanningEntry.PlanningResult planned =
                service.plan(result(RenderPlanStatus.PLANNABLE), planId);

        assertEquals(planId, planned.physicalExecutionPlan().planId(),
                "the caller-supplied plan identity is preserved verbatim");
        assertEquals(new RenderPlanFingerprint("fp-1"),
                planned.physicalExecutionPlan().planFingerprint(),
                "the physical plan carries the #20 semantic fingerprint");
    }

    @Test
    void planIsDeterministicForTheSameInput() {
        ExecutionPlanningEntry.PlanningResult first =
                service.plan(result(RenderPlanStatus.PLANNABLE), new ExecutionPlanId("pep-ppes-det"));
        ExecutionPlanningEntry.PlanningResult second =
                service.plan(result(RenderPlanStatus.PLANNABLE), new ExecutionPlanId("pep-ppes-det"));

        assertEquals(first.logicalExecutionGraph(), second.logicalExecutionGraph());
        assertEquals(first.physicalExecutionPlan(), second.physicalExecutionPlan());
        assertEquals(first.executionRequirement(), second.executionRequirement());
    }

    @Test
    void planFailsClosedWhenRenderResultIsNotPlannable() {
        for (RenderPlanStatus rejected : List.of(
                RenderPlanStatus.UNRENDERABLE, RenderPlanStatus.PREPARATION_REQUIRED)) {
            ExecutionPlanningException failure = assertThrows(ExecutionPlanningException.class,
                    () -> service.plan(result(rejected), new ExecutionPlanId("pep-ppes-reject")));
            assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE,
                    failure.reason(),
                    "non-PLANNABLE " + rejected + " is rejected with the typed render-status reason");
            assertFalse(failure.getMessage().isBlank());
        }
    }

    @Test
    void planRejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> service.plan(null, new ExecutionPlanId("pep-ppes-null")));
        assertThrows(NullPointerException.class,
                () -> service.plan(result(RenderPlanStatus.PLANNABLE), null));
    }

    @Test
    void planForAdmissionProducesThePlatformAdmissionContract() {
        PlatformExecutionPlan admitted = service.planForAdmission(
                result(RenderPlanStatus.PLANNABLE), new ExecutionPlanId("pep-ppes-admit"), identity());

        assertEquals(new ExecutionPlanId("pep-ppes-admit"), admitted.planId());
        assertEquals("render", admitted.operation().capability());
        assertEquals("render-plan", admitted.operation().operation());
        assertEquals("rev-1", admitted.source().sourceId());
        assertEquals(identity().scope(), admitted.scope());
        assertEquals("idem-ppes-1", admitted.idempotency().key());
        assertEquals("hash-ppes-1", admitted.idempotency().requestHash());
        assertEquals("quota-1", admitted.quota().snapshotId());
        assertEquals("corr-1", admitted.audit().correlationId());
        assertFalse(admitted.inputs().isEmpty(), "typed inputs projected from the render nodes");
        assertFalse(admitted.outputs().isEmpty(), "typed outputs projected from the request outputs");
    }

    @Test
    void planForAdmissionFailsClosedWhenNotPlannable() {
        ExecutionPlanningException failure = assertThrows(ExecutionPlanningException.class,
                () -> service.planForAdmission(result(RenderPlanStatus.UNRENDERABLE),
                        new ExecutionPlanId("pep-ppes-admit-reject"), identity()));
        assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE, failure.reason());
    }

    @Test
    void admissionIdentityRejectsMissingComponents() {
        var scope = new PlatformExecutionPlan.Scope("t", "w", "a");
        var quota = new PlatformExecutionPlan.EntitlementQuotaSnapshot("q", Map.of(), 1L);
        var audit = new PlatformExecutionPlan.CorrelationAuditIdentity("c", "a");

        assertThrows(NullPointerException.class,
                () -> new PhysicalPlanningEntryService.AdmissionIdentity(null, "i", "h", quota, audit));
        assertThrows(NullPointerException.class,
                () -> new PhysicalPlanningEntryService.AdmissionIdentity(scope, null, "h", quota, audit));
        assertThrows(NullPointerException.class,
                () -> new PhysicalPlanningEntryService.AdmissionIdentity(scope, "i", null, quota, audit));
        assertThrows(NullPointerException.class,
                () -> new PhysicalPlanningEntryService.AdmissionIdentity(scope, "i", "h", null, audit));
        assertThrows(NullPointerException.class,
                () -> new PhysicalPlanningEntryService.AdmissionIdentity(scope, "i", "h", quota, null));
    }

    @Test
    void planForAdmissionRejectsNullIdentity() {
        assertThrows(NullPointerException.class,
                () -> service.planForAdmission(result(RenderPlanStatus.PLANNABLE),
                        new ExecutionPlanId("pep-ppes-null-identity"), null));
    }

    @Test
    void logicalGraphRetainsTheRenderPlanFingerprint() {
        ExecutionPlanningEntry.PlanningResult planned =
                service.plan(result(RenderPlanStatus.PLANNABLE), new ExecutionPlanId("pep-ppes-fp"));

        assertEquals(new RenderPlanFingerprint("fp-1"), planned.logicalExecutionGraph().planFingerprint(),
                "logical graph is bound to the exact #20 plan fingerprint");
        assertTrue(planned.logicalExecutionGraph().nodes().stream()
                        .allMatch(n -> n.operationKey() != null && !n.operationKey().isBlank()),
                "logical nodes retain their typed operation keys");
    }
}
