package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.*;
import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelinePastePlannerTest {
    private static final TimelineClipId FIRST = TimelineClipId.of("red");
    private static final TimelineClipId SECOND = TimelineClipId.of("blue");
    private static final TimelineClipId NEW_FIRST = TimelineClipId.of("red-copy");
    private static final TimelineClipId NEW_SECOND = TimelineClipId.of("blue-copy");
    private final TimelineContentDigester digester = new TimelineContentDigester();

    @Test
    void copiesRelativePlacementAndExactValuesWithStableIdentities() {
        TimelineDocument base = document();
        var plan = plan(base, List.of(FIRST, SECOND), List.of(NEW_FIRST, NEW_SECOND), time(10));
        var result = plan.candidateTimeline();
        var original = base.getTracks().getFirst().clips();
        var all = result.getTracks().getFirst().clips();
        assertEquals(original, all.subList(0, 2));
        assertEquals(List.of(NEW_FIRST, NEW_SECOND), all.subList(2, 4).stream().map(TimelineClip::getClipId).toList());
        assertEquals(List.of(time(10), time(14)), all.subList(2, 4).stream().map(TimelineClip::getStartTime).toList());
        assertEquals(List.of(time(12), time(15)), all.subList(2, 4).stream().map(TimelineClip::getEndTime).toList());
        for (int i = 0; i < 2; i++) {
            var source = original.get(i);
            var copy = all.get(i + 2);
            assertEquals(source.getArtifactId(), copy.getArtifactId());
            assertEquals(source.getContentDigest(), copy.getContentDigest());
            assertEquals(source.getMediaStreamId(), copy.getMediaStreamId());
            assertEquals(source.getMediaAssetId(), copy.getMediaAssetId());
            assertEquals(source.getTrimStart(), copy.getTrimStart());
            assertEquals(source.getTrimEnd(), copy.getTrimEnd());
            assertEquals(source.getTemporalMapping(), copy.getTemporalMapping());
            assertEquals(source.getEffects().stream().map(TimelineClipEffect::effectKey).toList(),
                    copy.getEffects().stream().map(TimelineClipEffect::effectKey).toList());
            assertEquals(source.getEffects().stream().map(TimelineClipEffect::parameters).toList(),
                    copy.getEffects().stream().map(TimelineClipEffect::parameters).toList());
            assertEquals(source.getTransform(), copy.getTransform());
        }
        assertNotEquals(original.getFirst().getEffects().getFirst().id(), all.get(2).getEffects().getFirst().id());
        var routes = result.getAudioMix().routes();
        assertEquals(base.getAudioMix().routes().getFirst(), routes.getFirst());
        assertEquals(AudioRoute.of(AudioMixInput.of("video", NEW_FIRST.value()), new AudioGain(0.5)), routes.getLast());
        assertEquals(plan.planDigest(), plan(base, List.of(FIRST, SECOND), List.of(NEW_FIRST, NEW_SECOND), time(10)).planDigest());
    }

    @Test
    void invalidSecondIdentityAndCollisionDoNotAlterTheBase() {
        TimelineDocument base = document();
        String before = digester.digest(base);
        assertEquals(PlanErrorCode.PLACEMENT_CONFLICT, assertThrows(PlanException.class,
                () -> plan(base, List.of(FIRST, SECOND), List.of(NEW_FIRST, SECOND), time(10))).code());
        assertEquals(PlanErrorCode.PLACEMENT_CONFLICT, assertThrows(PlanException.class,
                () -> plan(base, List.of(FIRST), List.of(NEW_FIRST), time(1))).code());
        assertEquals(PlanErrorCode.INVALID_PLAN, assertThrows(PlanException.class,
                () -> plan(base, List.of(SECOND, FIRST), List.of(NEW_FIRST, NEW_SECOND), time(10))).code());
        assertEquals(before, digester.digest(base));
    }

    @Test
    void rejectsCopyOfAnAutomatedInlineEffect() {
        TimelineDocument base = document();
        var automated = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), List.of(), List.of(), List.of(),
                List.of(new CanonicalAutomationCurve("curve", "fx-local", "brightness", "NUMBER", "HOLD", List.of())));
        var failure = assertThrows(PlanException.class,
                () -> plan(automated, List.of(FIRST), List.of(NEW_FIRST), time(10)));
        assertTrue(failure.getMessage().contains("automation"));
    }

    private OperationPlan plan(TimelineDocument base, List<TimelineClipId> sources, List<TimelineClipId> copies, MediaTime at) {
        var def = OperationDefinition.V3.PASTE_CLIPS;
        String hash = digester.digest(base);
        var request = new OperationRequest(def.definitionId(), def.version(),
                new OperationTargetRequest.TimelineTargetRequest("project"),
                new OperationParameters.PasteClipsParameters("video", sources, copies, at), "r1", hash, null);
        var instance = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("r1", hash, base, "project"));
        return new OperationPlanner().plan(instance, "r1", base);
    }

    private static TimelineDocument document() {
        var red = new TimelineClip(FIRST.value(), "asset-red", "stream-red", "artifact-red", "a".repeat(64),
                time(1), time(3), time(2), time(6), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.REVERSE),
                List.of(new TimelineClipEffect("fx-local", BasicVideoEffect.EFFECT_KEY,
                        new BasicVideoEffect(0.1, 1, 1, false, 0, 0, true).toParameters())), VideoTransform.IDENTITY);
        var blue = new TimelineClip(SECOND.value(), "asset-blue", "stream-blue", "artifact-blue", "b".repeat(64),
                time(5), time(6), time(0), time(1), "MEDIA_STREAM", ConstantRateTemporalMapping.identity());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video", "Video", TrackType.VIDEO, List.of(red, blue))),
                TimelineMetadata.empty(), new AudioMix(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("video", FIRST.value()), new AudioGain(0.5)))));
    }

    private static MediaTime time(long seconds) { return MediaTime.ofRational(seconds, 1); }
}
