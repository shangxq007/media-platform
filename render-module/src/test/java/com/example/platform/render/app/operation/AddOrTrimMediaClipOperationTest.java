package com.example.platform.render.app.operation;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationErrorCode;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationResolutionException;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.OperationPlanPreview;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlannedChange;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** H7 authoritative cross-module semantic scenario through REQUEST -> RESOLVE -> PLAN -> PREVIEW. */
class AddOrTrimMediaClipOperationTest {

    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void addsExactPinnedSourceRangeAtExactPlacementDeterministically() throws Exception {
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "main", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
        String baseHash = new TimelineContentDigester().digest(base);
        var sourceRange = new MediaClip.TimeRange(
                MediaTime.ofRational(10, 1), MediaTime.ofRational(20, 1));
        var binding = TestSourceBindings.of(
                "media-S", "stream-S-video", "artifact-S-v1", sourceRange);
        var placement = new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        var mapping = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD);
        var parameters = new OperationParameters.AddOrTrimMediaClipParameters(
                "video-1", TimelineClipId.of("clip-S-10-20"), binding, placement, mapping);
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.ADD_OR_TRIM_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_OR_TRIM_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-T"),
                parameters, "revision-R0", baseHash, null);

        var instance = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext(
                        "revision-R0", baseHash, base, "timeline-T"));
        var plan = new OperationPlanner().plan(instance, base);
        var preview = OperationPlanPreview.of(plan);

        assertFalse(plan.noOp());
        assertNotEquals(baseHash, plan.candidateContentHash());
        assertEquals(List.of("add(video-1,clip-S-10-20)"), preview.primaryChanges());
        PlannedChange.ClipAdded change = (PlannedChange.ClipAdded) plan.plannedChanges().getFirst();
        assertEquals(MediaTime.ofRational(10, 1), change.newClip().getTrimStart());
        assertEquals(MediaTime.ofRational(20, 1), change.newClip().getTrimEnd());
        assertEquals(MediaTime.ZERO, change.newClip().getStartTime());
        assertEquals(MediaTime.ofRational(10, 1), change.newClip().getEndTime());
        assertEquals(mapping, change.newClip().getTemporalMapping());
        assertEquals("artifact-S-v1", change.newClip().getArtifactId());
        assertEquals(DIGEST, change.newClip().getContentDigest());

        String first = TimelineDocumentJsonSerializer.serialize(plan.candidateTimeline());
        String second = TimelineDocumentJsonSerializer.serialize(
                new OperationPlanner().plan(instance, base).candidateTimeline());
        assertEquals(first, second, "canonical serialization must be deterministic");
        assertEquals(plan.candidateContentHash(),
                new TimelineContentDigester().digest(plan.candidateTimeline()));
    }

    @Test
    void mismatchedTargetTimelineFailsWithoutMutableLatestFallback() {
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "main", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
        var sourceRange = new MediaClip.TimeRange(
                MediaTime.ofRational(10, 1), MediaTime.ofRational(20, 1));
        var binding = TestSourceBindings.of(
                "media-S", "stream-S-video", "artifact-S-v1", sourceRange);
        OperationRequest request = new OperationRequest(
                OperationDefinition.V1.ADD_OR_TRIM_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_OR_TRIM_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest("timeline-other"),
                new OperationParameters.AddOrTrimMediaClipParameters(
                        "video-1", TimelineClipId.of("clip-S"), binding,
                        new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
                        ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD)),
                "revision-R0", new TimelineContentDigester().digest(base), null);
        OperationResolutionException failure = assertThrows(OperationResolutionException.class,
                () -> OperationRequestResolver.resolve(request,
                        new OperationRequestResolver.OperationBaseContext(
                                "revision-R0", request.baseContentHash(), base, "timeline-T")));
        assertEquals(OperationErrorCode.INVALID_SCOPE, failure.code());
    }
}
