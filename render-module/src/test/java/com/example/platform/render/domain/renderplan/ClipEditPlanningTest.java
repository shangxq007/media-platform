package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClipEditPlanningTest {

    private static final String REV = "rev-edit";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void moveTrimDeleteChangeRenderPlanIdentityAndSourceSampling() {
        TimelineDocument base = timeline();
        VerifiedEffectSemanticSnapshot effects = TestPlans.canonicalInput().effectSemanticSnapshot();
        RenderPlanningResult basePlan = plan(base, effects, MediaTime.ofRational(5, 2));
        assertThat(basePlan.status()).isEqualTo(RenderPlanStatus.PLANNABLE);
        long baseDecodes = decodeCount(basePlan);
        assertThat(baseDecodes).isEqualTo(2);

        TimelineDocument moved = operation(base, OperationDefinition.V1.MOVE,
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 2), false), "blue");
        RenderPlanningResult movedPlan = plan(moved, effects, MediaTime.ofRational(3, 1));
        assertThat(movedPlan.plan().fingerprint()).isNotEqualTo(basePlan.plan().fingerprint());
        assertThat(decodeCount(movedPlan)).isEqualTo(2);

        TimelineDocument trimmed = operation(base, OperationDefinition.V1.TRIM,
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 2)), "blue");
        RenderPlanningResult trimmedPlan = plan(trimmed, effects, MediaTime.ofRational(2, 1));
        assertThat(trimmedPlan.plan().fingerprint()).isNotEqualTo(basePlan.plan().fingerprint());
        assertThat(trimmed.getTracks().getFirst().clips().get(1).getEndTime())
                .isEqualTo(MediaTime.ofRational(3, 2));
        RenderSampleWindow blueWindow = decodeWindow(trimmedPlan, "blue");
        assertThat(blueWindow.start()).isEqualTo(MediaTime.ZERO);
        assertThat(blueWindow.end()).isEqualTo(MediaTime.ofRational(1, 2));
        assertThat(blueWindow.frameRate()).isEqualTo(FrameRate.of(2, 1));

        TimelineDocument deleted = operation(base, OperationDefinition.V1.DELETE,
                new OperationParameters.NoParameters(), "blue");
        RenderPlanningResult deletedPlan = plan(deleted, effects, MediaTime.ofRational(2, 1));
        assertThat(deletedPlan.plan().fingerprint()).isNotEqualTo(basePlan.plan().fingerprint());
        assertThat(decodeCount(deletedPlan)).isEqualTo(1);
        assertThat(deleted.getTracks().getFirst().clips())
                .extracting(clip -> clip.getClipId().value())
                .containsExactly("red");
    }

    private static RenderPlanningResult plan(TimelineDocument document, VerifiedEffectSemanticSnapshot effects,
                                              MediaTime end) {
        VerifiedTimelineRevision verified = VerifiedTimelineRevisionFactory.verified(
                revision(document, effects.reference()), DIGESTER);
        VerifiedRenderSemanticSnapshot snapshot = new VerifiedRenderSemanticSnapshot(verified, effects);
        RenderPlanningInput input = new RenderPlanningInput(snapshot,
                new RenderRequest(new RenderRequestId("edit-request"),
                        new RenderExtent(MediaTime.ZERO, end, FrameRate.of(2, 1)),
                        List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER))),
                new SourceResolutionInput(Map.of(
                        new ArtifactId("art-red"), RenderSourceResolutionState.RESOLVED,
                        new ArtifactId("art-blue"), RenderSourceResolutionState.RESOLVED)),
                new CapabilityContext(Set.of(
                        RenderCapabilityVocabulary.videoDecode().capabilityId(),
                        RenderCapabilityVocabulary.composite().capabilityId(),
                        RenderCapabilityVocabulary.outputEncode().capabilityId())));
        return new DefaultRenderPlanner().plan(input);
    }

    private static long decodeCount(RenderPlanningResult result) {
        return result.graph().nodes().stream()
                .filter(node -> node.kind() instanceof RenderNodeKind.Decode)
                .count();
    }

    private static RenderSampleWindow decodeWindow(RenderPlanningResult result, String clipId) {
        return result.graph().nodes().stream()
                .filter(node -> node.kind() instanceof RenderNodeKind.Decode)
                .filter(node -> node.id().value().contains(clipId))
                .findFirst().orElseThrow().requiredSampleWindow().orElseThrow();
    }

    private static TimelineDocument operation(TimelineDocument base, OperationDefinition definition,
                                               OperationParameters parameters, String clipId) {
        OperationInstance instance = new OperationInstance(definition.definitionId(), definition.version(),
                REV, DIGESTER.digest(base),
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV,
                        DIGESTER.digest(base),
                        List.of(com.example.platform.timeline.canonical.TimelineClipId.of(clipId)),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                parameters, "parameter-digest", "invocation");
        OperationPlan plan = new OperationPlanner().plan(instance, REV, base);
        return plan.candidateTimeline();
    }

    private static TimelineRevision revision(TimelineDocument document, EffectSemanticSnapshotReference effectRef) {
        String timelineDigest = DIGESTER.digest(document);
        String fullDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, effectRef);
        return new TimelineRevision(REV, "project", null, TimelineDocument.CURRENT_SCHEMA_VERSION,
                document, fullDigest, Instant.EPOCH, "test",
                new TimelineRevisionSemanticContext(timelineDigest, effectRef, fullDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static TimelineDocument timeline() {
        TimelineClip red = new TimelineClip("red", "media-red", "stream-red", "art-red", HASH,
                MediaTime.ZERO, MediaTime.ofRational(1, 2),
                MediaTime.ZERO, MediaTime.ofRational(1, 2), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        TimelineClip blue = new TimelineClip("blue", "media-blue", "stream-blue", "art-blue", HASH,
                MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1),
                MediaTime.ZERO, MediaTime.ofRational(1, 1), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "main", TrackType.VIDEO, List.of(red, blue))),
                TimelineMetadata.empty());
    }
}
