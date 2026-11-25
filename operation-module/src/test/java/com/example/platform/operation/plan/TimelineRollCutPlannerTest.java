package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.*;
import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRollCutPlannerTest {
    private final ConstantRateTemporalMapping identity = ConstantRateTemporalMapping.identity();
    private final TimelineClip left = clip("left", "2", "5", "1", "4", identity);
    private final TimelineClip right = clip("right", "5", "9", "2", "6", identity);

    @Test
    void preservesEffectsRoutesAndOuterBoundariesForFractionalRoll() {
        var base = document(left, right);
        var result = plan(base, "151/30").candidateTimeline();
        var clips = result.getTracks().getFirst().clips();
        assertEquals(time("151/30"), clips.getFirst().getEndTime());
        assertEquals(time("121/30"), clips.getFirst().getTrimEnd());
        assertEquals(time("151/30"), clips.getLast().getStartTime());
        assertEquals(time("61/30"), clips.getLast().getTrimStart());
        assertEquals(left.getStartTime(), clips.getFirst().getStartTime());
        assertEquals(right.getEndTime(), clips.getLast().getEndTime());
        assertEquals(left.getEffects(), clips.getFirst().getEffects());
        assertEquals(right.getEffects(), clips.getLast().getEffects());
        assertEquals(base.getAudioMix(), result.getAudioMix());
        assertTrue(plan(base, "5").noOp());
    }

    @Test
    void rejectsThirdClipOverlappingEitherSideAndUnsupportedMappings() {
        for (String start : List.of("3", "7")) {
            var base = document(left, right, clip("other", start, "8", "0", "1", identity));
            assertTrue(assertThrows(PlanException.class, () -> plan(base, "6")).getMessage().contains("overlapping"));
        }
        for (var mapping : List.of(ConstantRateTemporalMapping.of(2,1, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(1,1, PlaybackDirection.REVERSE))) {
            var base = document(left, clip("right", "5", "9", "2", "6", mapping));
            assertTrue(assertThrows(PlanException.class, () -> plan(base, "6")).getMessage().contains("forward 1x"));
        }
    }

    @Test
    void rejectsAffectedRelationshipsAndAutomationOnEitherSide() {
        var base = document(left, right);
        var synced = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), List.of(SyncRelationship.of(left.getClipId(), time("2"), right.getClipId(), time("5"))));
        assertTrue(assertThrows(PlanException.class, () -> plan(synced, "6")).getMessage().contains("relationship"));
        for (var target : List.of("fx-left", "right")) {
            var automated = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                    base.getAudioMix(), List.of(), List.of(), List.of(),
                    List.of(new CanonicalAutomationCurve("curve", target, "brightness", "NUMBER", "HOLD", List.of())));
            assertTrue(assertThrows(PlanException.class, () -> plan(automated, "6")).getMessage().contains("automation"));
        }
    }

    private OperationPlan plan(TimelineDocument base, String cut) {
        var def = OperationDefinition.V3.ROLL_CUT;
        String hash = new TimelineContentDigester().digest(base);
        var request = new OperationRequest(def.definitionId(), def.version(), new OperationTargetRequest.TimelineTargetRequest("project"),
                new OperationParameters.RollCutParameters("video", left.getClipId(), right.getClipId(), time(cut)), "base", hash, null);
        return new OperationPlanner().plan(OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("base", hash, base, "project")), "base", base);
    }

    private static TimelineDocument document(TimelineClip... clips) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video", "Video", TrackType.VIDEO, List.of(clips))), TimelineMetadata.empty(),
                new AudioMix(AudioMasterBus.master(), List.of(AudioRoute.of(AudioMixInput.of("video", "right")))), List.of());
    }

    private static TimelineClip clip(String id, String start, String end, String from, String to, ConstantRateTemporalMapping mapping) {
        var fx = new TimelineClipEffect("fx-" + id, BasicVideoEffect.EFFECT_KEY,
                new BasicVideoEffect(0.1, 1, 1, false, 0, 0, true).toParameters());
        return new TimelineClip(id, "asset", "stream", "artifact", "a".repeat(64), time(start), time(end), time(from),
                time(to), "MEDIA_STREAM", mapping, List.of(fx), VideoTransform.IDENTITY);
    }

    private static MediaTime time(String value) {
        return MediaTime.parse(value.contains("/") ? value : value + "/1");
    }
}
