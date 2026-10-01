package com.example.platform.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cross-stage integration coverage: the P2-1 #20 production entry
 * ({@link RenderPlanningEntryService}) feeds real {@code RenderPlanningResult}s
 * into the P2-2 #21 production entry ({@link PhysicalPlanningEntryService}),
 * proving the #20 → #21 handoff end to end without any provider, worker,
 * database, or Temporal involvement.
 */
class PhysicalPlanningEntryServiceIntegrationTest {

    private static final String REVISION_ID = "rev-p21-p22";
    private static final String ARTIFACT_ID = "art-p21-p22";
    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory())
            .mintEmpty();

    private final RenderPlanningEntryService renderPlanningEntry = new RenderPlanningEntryService();
    private final PhysicalPlanningEntryService physicalPlanningEntry = new PhysicalPlanningEntryService();

    // ---------- fixtures ----------

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                "clip-p21-p22", "asset-1", "stream-1", ARTIFACT_ID, "a".repeat(64),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "v1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of());
    }

    private static TimelineRevision revision(TimelineDocument document) {
        String timelineDigest = new TimelineContentDigester().digest(document);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                REVISION_ID,
                "product-p21-p22",
                null,
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                document,
                revisionSemanticDigest,
                Instant.EPOCH,
                "p21-p22-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest,
                        PINNED_EMPTY.reference(),
                        revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static RenderRequest request() {
        return new RenderRequest(
                new RenderRequestId("req-p21-p22"),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
    }

    private static CapabilityContext capabilities() {
        return new CapabilityContext(Set.of(
                CapabilityId.of("video.decode"),
                CapabilityId.of("render.composite"),
                CapabilityId.of("render.output")));
    }

    private static SourceResolutionInput sources(RenderSourceResolutionState state) {
        return new SourceResolutionInput(Map.of(new ArtifactId(ARTIFACT_ID), state));
    }

    private RenderPlanningResult planRender(RenderSourceResolutionState sourceState) {
        TimelineDocument document = document();
        return renderPlanningEntry.plan(
                revision(document), PINNED_EMPTY, request(), sources(sourceState), capabilities());
    }

    private static PhysicalPlanningEntryService.AdmissionIdentity identity() {
        return new PhysicalPlanningEntryService.AdmissionIdentity(
                new PlatformExecutionPlan.Scope("tenant-1", "workspace-1", "actor-1"),
                "idem-p21-p22",
                "hash-p21-p22",
                new PlatformExecutionPlan.EntitlementQuotaSnapshot("quota-1", Map.of(), 5L),
                new PlatformExecutionPlan.CorrelationAuditIdentity("corr-1", "audit-1"));
    }

    // ---------- tests ----------

    @Test
    void realRevisionFlowsFromStage20IntoStage21() {
        RenderPlanningResult renderResult = planRender(RenderSourceResolutionState.RESOLVED);
        assertEquals(RenderPlanStatus.PLANNABLE, renderResult.status(),
                "the real revision renders a PLANNABLE #20 result");

        ExecutionPlanningEntry.PlanningResult planned =
                physicalPlanningEntry.plan(renderResult, new ExecutionPlanId("pep-p21-p22"));

        assertNotNull(planned.executionRequirement());
        assertNotNull(planned.logicalExecutionGraph());
        assertNotNull(planned.physicalExecutionPlan());
        assertEquals(renderResult.graph().nodes().size(),
                planned.logicalExecutionGraph().nodes().size(),
                "#21 projects every #20 graph node 1:1 into a logical node");
        assertEquals(planned.logicalExecutionGraph().nodes().size(),
                planned.physicalExecutionPlan().units().size(),
                "#21 projects every logical node 1:1 into a physical plan unit");
        assertEquals(renderResult.plan().fingerprint(),
                planned.physicalExecutionPlan().planFingerprint(),
                "the physical plan is bound to the exact #20 plan fingerprint");
        assertFalse(planned.physicalExecutionPlan().units().isEmpty());
        assertTrue(planned.physicalExecutionPlan().units().stream()
                        .allMatch(unit -> unit.sourceRenderNodeId() != null),
                "every physical unit retains its typed #20 source node identity");
    }

    @Test
    void unrenderableStage20ResultIsRejectedByStage21() {
        RenderPlanningResult renderResult = planRender(RenderSourceResolutionState.FAILED);
        assertEquals(RenderPlanStatus.UNRENDERABLE, renderResult.status(),
                "a failed source resolution yields an UNRENDERABLE #20 result");

        ExecutionPlanningException failure = assertThrows(ExecutionPlanningException.class,
                () -> physicalPlanningEntry.plan(renderResult, new ExecutionPlanId("pep-p21-p22-reject")));
        assertEquals(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE, failure.reason(),
                "the #21 entry fails closed with the typed render-status reason");
    }

    @Test
    void admissionProjectionCarriesTheRealRevisionIdentity() {
        RenderPlanningResult renderResult = planRender(RenderSourceResolutionState.RESOLVED);

        PlatformExecutionPlan admitted = physicalPlanningEntry.planForAdmission(
                renderResult, new ExecutionPlanId("pep-p21-p22-admit"), identity());

        assertEquals(REVISION_ID, admitted.source().sourceId(),
                "the admission contract carries the authored revision identity");
        assertEquals(renderResult.plan().revision().contentDigest().value(), admitted.source().revision(),
                "the admission contract carries the exact verified revision content digest");
        assertEquals(new ExecutionPlanId("pep-p21-p22-admit"), admitted.planId());
    }

    @Test
    void identicalRevisionsProduceIdenticalStage21Plans() {
        ExecutionPlanId planId = new ExecutionPlanId("pep-p21-p22-det");

        ExecutionPlanningEntry.PlanningResult first =
                physicalPlanningEntry.plan(planRender(RenderSourceResolutionState.RESOLVED), planId);
        ExecutionPlanningEntry.PlanningResult second =
                physicalPlanningEntry.plan(planRender(RenderSourceResolutionState.RESOLVED), planId);

        assertEquals(first.logicalExecutionGraph(), second.logicalExecutionGraph());
        assertEquals(first.physicalExecutionPlan(), second.physicalExecutionPlan());
    }
}
