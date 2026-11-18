package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.ParameterDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPLIT time semantics: one clip becomes two adjacent clips with exact source mapping,
 * inherited attributes, no ripple/gap/overlap, deterministic identity and fail-closed
 * handling of states the planner cannot split safely.
 */
class TimelineSplitPlannerTest {

    private static final String REV = "revision-1";
    private static final String HASH = "hash-1";
    private static final TimelineClipId LEFT = TimelineClipId.of("clip-a");
    private static final TimelineClipId RIGHT = TimelineClipId.of("clip-b");

    private final OperationPlanner planner = new OperationPlanner();
    private final TimelineContentDigester digester = new TimelineContentDigester();

    @Test
    void splitsForwardAtTheExactSourceAndTimelineBoundary() {
        TimelineDocument base = document(clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of()));

        OperationPlan plan = plan(base, LEFT, RIGHT, "23/1");

        TimelineDocument candidate = plan.candidateTimeline();
        List<TimelineClip> clips = candidate.getTracks().get(0).clips();
        assertEquals(List.of(LEFT, RIGHT), clips.stream().map(TimelineClip::getClipId).toList());
        TimelineClip left = clips.get(0);
        TimelineClip right = clips.get(1);
        assertEquals(MediaTime.ofRational(20, 1), left.getStartTime());
        assertEquals(MediaTime.ofRational(23, 1), left.getEndTime());
        assertEquals(MediaTime.ofRational(10, 1), left.getTrimStart());
        assertEquals(MediaTime.ofRational(13, 1), left.getTrimEnd());
        assertEquals(MediaTime.ofRational(23, 1), right.getStartTime());
        assertEquals(MediaTime.ofRational(26, 1), right.getEndTime());
        assertEquals(MediaTime.ofRational(13, 1), right.getTrimStart());
        assertEquals(MediaTime.ofRational(16, 1), right.getTrimEnd());
        assertFalse(plan.noOp());
    }

    @Test
    void splitMustBeStrictlyInsideTheClip() {
        TimelineDocument base = document(clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of()));
        for (String time : List.of("20/1", "26/1", "19/1", "27/1")) {
            PlanException failure = assertThrows(PlanException.class,
                    () -> plan(base, LEFT, RIGHT, time));
            assertEquals(PlanErrorCode.INVALID_PLAN, failure.code(), time);
        }
    }

    @Test
    void rightIdentityMustBeNewAndDistinct() {
        TimelineDocument base = document(clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of()));
        PlanException same = assertThrows(PlanException.class,
                () -> plan(base, LEFT, LEFT, "23/1"));
        assertEquals(PlanErrorCode.INVALID_PLAN, same.code());
        PlanException existing = assertThrows(PlanException.class,
                () -> plan(documentWithSecondClip(), LEFT, TimelineClipId.of("clip-z"), "23/1"));
        assertEquals(PlanErrorCode.PLACEMENT_CONFLICT, existing.code());
    }

    @Test
    void inheritsMediaReferencesMappingTrackAndSourceKind() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        TimelineDocument base = document(source);

        OperationPlan plan = plan(base, LEFT, RIGHT, "23/1");

        TimelineClip right = plan.candidateTimeline().getTracks().get(0).clips().get(1);
        assertEquals(source.getMediaAssetId(), right.getMediaAssetId());
        assertEquals(source.getMediaStreamId(), right.getMediaStreamId());
        assertEquals(source.getArtifactId(), right.getArtifactId());
        assertEquals(source.getContentDigest(), right.getContentDigest());
        assertEquals(source.getSourceKind(), right.getSourceKind());
        assertEquals(source.getTemporalMapping(), right.getTemporalMapping());
        assertEquals("track-1", plan.candidateTimeline().getTracks().get(0).trackId());
    }

    @Test
    void totalDurationIsPreservedWithoutGapOrOverlap() {
        TimelineDocument base = document(clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of()));
        TimelineDocument candidate = plan(base, LEFT, RIGHT, "23/1").candidateTimeline();
        List<TimelineClip> clips = candidate.getTracks().get(0).clips();
        long totalTicks = clips.get(0).getEndTime().subtract(clips.get(0).getStartTime()).ticks()
                + clips.get(1).getEndTime().subtract(clips.get(1).getStartTime()).ticks();
        assertEquals(6, totalTicks);
        assertEquals(clips.get(0).getEndTime(), clips.get(1).getStartTime());
    }

    @Test
    void reversePlaybackSplitsAlongThePlaybackDirection() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE), List.of());

        TimelineDocument candidate = plan(document(source), LEFT, RIGHT, "23/1").candidateTimeline();
        TimelineClip left = candidate.getTracks().get(0).clips().get(0);
        TimelineClip right = candidate.getTracks().get(0).clips().get(1);
        assertEquals(MediaTime.ofRational(13, 1), left.getTrimStart());
        assertEquals(MediaTime.ofRational(16, 1), left.getTrimEnd());
        assertEquals(MediaTime.ofRational(10, 1), right.getTrimStart());
        assertEquals(MediaTime.ofRational(13, 1), right.getTrimEnd());
    }

    @Test
    void rateMappingScalesTheSourceDelta() {
        TimelineClip source = clip(LEFT, 0, 3, 0, 6,
                ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD), List.of());

        TimelineDocument candidate = plan(document(source), LEFT, RIGHT, "1/1").candidateTimeline();
        TimelineClip left = candidate.getTracks().get(0).clips().get(0);
        TimelineClip right = candidate.getTracks().get(0).clips().get(1);
        assertEquals(MediaTime.ofRational(2, 1), left.getTrimEnd());
        assertEquals(MediaTime.ofRational(2, 1), right.getTrimStart());
        assertEquals(MediaTime.ofRational(6, 1), right.getTrimEnd());
    }

    @Test
    void freezeMappingIsUnsupported() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                new FreezeTemporalMapping(MediaTime.ofRational(12, 1)), List.of());
        PlanException failure = assertThrows(PlanException.class,
                () -> plan(document(source), LEFT, RIGHT, "23/1"));
        assertEquals(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE, failure.code());
    }

    @Test
    void clipEffectsAreUnsupported() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(),
                List.of(new TimelineClipEffect("effect-1", "owned.effect", Map.of())));
        PlanException failure = assertThrows(PlanException.class,
                () -> plan(document(source), LEFT, RIGHT, "23/1"));
        assertEquals(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE, failure.code());
    }

    @Test
    void clipAudioRouteReferenceIsUnsupported() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of());
        AudioMix mix = new AudioMix(com.example.platform.audio.domain.mix.AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("track-1", LEFT.value()))));
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "main", TrackType.VIDEO, List.of(source))),
                TimelineMetadata.empty(), mix, List.of(), List.of(), List.of(), List.of());
        PlanException failure = assertThrows(PlanException.class,
                () -> plan(base, LEFT, RIGHT, "23/1"));
        assertEquals(PlanErrorCode.UNSUPPORTED_TEMPORAL_STATE, failure.code());
    }

    @Test
    void planAndParameterDigestsAreDeterministic() {
        TimelineDocument base = document(clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of()));
        OperationPlan first = plan(base, LEFT, RIGHT, "23/1");
        OperationPlan second = plan(base, LEFT, RIGHT, "23/1");
        assertEquals(first.planDigest(), second.planDigest());
        assertEquals(first.candidateContentHash(), second.candidateContentHash());
        assertNotEquals(first.planDigest(), plan(base, LEFT, RIGHT, "24/1").planDigest());

        String digestOne = ParameterDigest.compute(OperationDefinition.V3.SPLIT_CLIP.definitionId(),
                OperationDefinition.V3.SPLIT_CLIP.version(),
                new OperationParameters.SplitParameters(RIGHT, MediaTime.ofRational(23, 1)));
        String digestTwo = ParameterDigest.compute(OperationDefinition.V3.SPLIT_CLIP.definitionId(),
                OperationDefinition.V3.SPLIT_CLIP.version(),
                new OperationParameters.SplitParameters(RIGHT, MediaTime.ofRational(46, 2)));
        assertEquals(digestOne, digestTwo);
    }

    @Test
    void baseDocumentAndSourceMediaAreNotMutated() {
        TimelineClip source = clip(LEFT, 20, 26, 10, 16,
                ConstantRateTemporalMapping.identity(), List.of());
        TimelineDocument base = document(source);
        String baseHash = digester.digest(base);

        TimelineDocument candidate = plan(base, LEFT, RIGHT, "23/1").candidateTimeline();

        assertEquals(baseHash, digester.digest(base));
        assertSame(source, base.getTracks().get(0).clips().get(0));
        assertEquals(LEFT, candidate.getTracks().get(0).clips().get(0).getClipId());
        assertEquals(RIGHT, candidate.getTracks().get(0).clips().get(1).getClipId());
    }

    // ---- fixtures ----

    private OperationPlan plan(TimelineDocument base, TimelineClipId left, TimelineClipId right,
                               String splitTime) {
        OperationRequest request = new OperationRequest(
                OperationDefinition.V3.SPLIT_CLIP.definitionId(),
                OperationDefinition.V3.SPLIT_CLIP.version(),
                new OperationTargetRequest.ClipSelectionTargetRequest(
                        "timeline-1",
                        new SelectionSpec.ExplicitObjectSelection(List.of(left)),
                        SelectionSpec.ExpansionPolicy.EXACT),
                new OperationParameters.SplitParameters(right, MediaTime.parse(splitTime)),
                REV, HASH, null);
        var instance = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext(REV, HASH, base, "timeline-1"));
        return planner.plan(instance, REV, base);
    }

    private static TimelineDocument document(TimelineClip clip) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
    }

    private static TimelineDocument documentWithSecondClip() {
        TimelineClip first = clip(LEFT, 20, 26, 10, 16, ConstantRateTemporalMapping.identity(), List.of());
        TimelineClip second = clip(TimelineClipId.of("clip-z"), 30, 32, 0, 2,
                ConstantRateTemporalMapping.identity(), List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "main", TrackType.VIDEO, List.of(first, second))),
                TimelineMetadata.empty());
    }

    private static TimelineClip clip(TimelineClipId id, long start, long end, long trimStart,
                                     long trimEnd, TemporalMapping mapping,
                                     List<TimelineClipEffect> effects) {
        return new TimelineClip(id.value(), "asset-a", "stream-v", "artifact-a", "c".repeat(64),
                MediaTime.ofRational(start, 1), MediaTime.ofRational(end, 1),
                MediaTime.ofRational(trimStart, 1), MediaTime.ofRational(trimEnd, 1),
                "MEDIA_STREAM", mapping, effects);
    }
}
