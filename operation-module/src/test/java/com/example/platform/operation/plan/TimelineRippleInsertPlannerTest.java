package com.example.platform.operation.plan;

import com.example.platform.audio.domain.mix.*;
import com.example.platform.operation.operation.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRippleInsertPlannerTest {
    private final TimelineClip source = clip("a", "0/1", "61/30");
    private final TimelineClip later = clip("b", "9/1", "10/1");
    private final TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
            List.of(new TimelineTrack("video", "Video", TrackType.VIDEO, List.of(source, later))),
            TimelineMetadata.empty(), new AudioMix(AudioMasterBus.master(), List.of(
                    AudioRoute.of(AudioMixInput.of("video", "a"), new AudioGain(0.5)))), List.of());

    @Test
    void gapAlwaysShiftsTheLaterClipByTheFullFractionalDuration() {
        var result = insert("4/1", "video", List.of("a")).candidateTimeline();
        var clips = result.getTracks().getFirst().clips();
        var copy = clips.stream().filter(c -> c.getClipId().value().equals("copy-0")).findFirst().orElseThrow();
        var moved = clips.stream().filter(c -> c.getClipId().equals(later.getClipId())).findFirst().orElseThrow();
        assertEquals(MediaTime.parse("181/30"), copy.getEndTime());
        assertEquals(MediaTime.parse("331/30"), moved.getStartTime());
        assertEquals(MediaTime.parse("361/30"), moved.getEndTime());
        assertEquals(later.getEffects(), moved.getEffects());
        assertEquals(source.getContentDigest(), copy.getContentDigest());
        assertNotEquals(source.getEffects().getFirst().id(), copy.getEffects().getFirst().id());
        assertEquals(source.getEffects().getFirst().parameters(), copy.getEffects().getFirst().parameters());
        assertEquals(2, result.getAudioMix().routes().size());
        assertEquals(base.getAudioMix().routes().getFirst().gain(), result.getAudioMix().routes().getLast().gain());
    }

    @Test
    void endInsertsWithoutMovingAndAmbiguousOrUnsupportedRequestsFail() {
        assertTrue(insert("10/1", "video", List.of("a")).plannedChanges().stream()
                .noneMatch(PlannedChange.ClipReplaced.class::isInstance));
        assertTrue(assertThrows(PlanException.class, () -> insert("1/1", "video", List.of("a")))
                .getMessage().contains("inside clip a"));
        assertTrue(assertThrows(PlanException.class, () -> insert("4/1", "video", List.of("a", "b")))
                .getMessage().contains("one copied clip"));
        assertThrows(PlanException.class, () -> insert("4/1", "other", List.of("a")));
    }

    private OperationPlan insert(String position, String track, List<String> ids) {
        var def = OperationDefinition.V3.RIPPLE_INSERT;
        var copies = java.util.stream.IntStream.range(0, ids.size()).mapToObj(i -> TimelineClipId.of("copy-" + i)).toList();
        var parameters = new OperationParameters.PasteClipsParameters(track, ids.stream().map(TimelineClipId::of).toList(),
                copies, MediaTime.parse(position));
        String hash = new TimelineContentDigester().digest(base);
        var request = new OperationRequest(def.definitionId(), def.version(), new OperationTargetRequest.TimelineTargetRequest("project"),
                parameters, "base", hash, null);
        return new OperationPlanner().plan(OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("base", hash, base, "project")), "base", base);
    }

    private static TimelineClip clip(String id, String start, String end) {
        var effect = new TimelineClipEffect("fx-" + id, BasicVideoEffect.EFFECT_KEY,
                new BasicVideoEffect(0.1, 1, 1, false, 0, 0, true).toParameters());
        return new TimelineClip(id, "asset", "stream", "artifact", "a".repeat(64), MediaTime.parse(start),
                MediaTime.parse(end), MediaTime.ZERO, MediaTime.parse(end).subtract(MediaTime.parse(start)),
                "MEDIA_STREAM", ConstantRateTemporalMapping.identity(), List.of(effect), VideoTransform.IDENTITY);
    }
}
