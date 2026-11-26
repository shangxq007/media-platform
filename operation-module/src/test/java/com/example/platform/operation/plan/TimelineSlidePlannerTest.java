package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.*;
import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineSlidePlannerTest {
    private final ConstantRateTemporalMapping identity = ConstantRateTemporalMapping.identity();
    private final TimelineClip left = clip("left", "2", "5", "1", "4", identity);
    private final TimelineClip right = clip("right", "8", "12", "2", "6", identity);

    private final TimelineClip middle = clip("middle", "5", "8", "2", "5", identity);

    @Test
    void slidesBothCutsExactlyWhilePreservingAuthoredState() {
        var base = document(left, middle, right);
        var result = plan(base, "+1/30").candidateTimeline();
        var clips = result.getTracks().getFirst().clips();
        assertEquals(time("151/30"), clips.getFirst().getEndTime());
        assertEquals(time("121/30"), clips.getFirst().getTrimEnd());
        assertEquals(time("151/30"), clips.get(1).getStartTime());
        assertEquals(time("241/30"), clips.get(1).getEndTime());
        assertEquals(middle.getTrimStart(), clips.get(1).getTrimStart());
        assertEquals(middle.getTrimEnd(), clips.get(1).getTrimEnd());
        assertEquals(time("241/30"), clips.getLast().getStartTime());
        assertEquals(time("61/30"), clips.getLast().getTrimStart());
        assertEquals(left.getStartTime(), clips.getFirst().getStartTime());
        assertEquals(right.getEndTime(), clips.getLast().getEndTime());
        for (int i = 0; i < clips.size(); i++) {
            var original = base.getTracks().getFirst().clips().get(i);
            assertEquals(original.getEffects(), clips.get(i).getEffects());
            assertEquals(original.getClipId(), clips.get(i).getClipId());
        }
        assertEquals(base.getAudioMix(), result.getAudioMix());
        assertTrue(plan(base, "0").noOp());
    }

    @Test
    void refusesMissingAmbiguousOrIncorrectNeighborsAndOverlaps() {
        var gap = clip("left", "2", "4", "1", "3", identity);
        var wrong = clip("other", "2", "5", "1", "4", identity);
        for (var base : List.of(document(gap, middle, right), document(wrong, middle, right),
                document(left, wrong, middle, right), document(left, middle))) {
            assertTrue(assertThrows(PlanException.class, () -> plan(base, "1")).getMessage().contains("touching"));
        }
        var overlap = document(left, middle, right, clip("other", "3", "4", "0", "1", identity));
        assertTrue(assertThrows(PlanException.class, () -> plan(overlap, "1")).getMessage().contains("overlap"));
    }

    @Test
    void checksMappingAndAutomationOnEveryParticipant() {
        var reverse = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE);
        var base = document(left, middle, right);
        for (int i = 0; i < 3; i++) {
            var clips = new TimelineClip[]{left, middle, right};
            var selected = clips[i];
            clips[i] = clip(selected.getClipId().value(), selected.getStartTime().toString(), selected.getEndTime().toString(),
                    selected.getTrimStart().toString(), selected.getTrimEnd().toString(), reverse);
            assertTrue(assertThrows(PlanException.class, () -> plan(document(clips), "1")).getMessage().contains("forward 1x"));
            var curve = new CanonicalAutomationCurve("curve", "fx-" + selected.getClipId().value(), "brightness", "NUMBER", "HOLD", List.of());
            var automated = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                    base.getAudioMix(), List.of(), List.of(), List.of(), List.of(curve));
            assertTrue(assertThrows(PlanException.class, () -> plan(automated, "1")).getMessage().contains("automation"));
        }
    }

    private OperationPlan plan(TimelineDocument base, String offset) {
        var def = OperationDefinition.V3.SLIDE_CLIP;
        String hash = new TimelineContentDigester().digest(base);
        var request = new OperationRequest(def.definitionId(), def.version(), new OperationTargetRequest.TimelineTargetRequest("project"),
                new OperationParameters.SlideParameters("video", left.getClipId(), middle.getClipId(), right.getClipId(),
                        OperationParameters.SignedTimeOffset.parse(offset)), "base", hash, null);
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
