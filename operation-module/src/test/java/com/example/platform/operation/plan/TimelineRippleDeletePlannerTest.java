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
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRippleDeletePlannerTest {
    private final TimelineContentDigester digester = new TimelineContentDigester();

    @Test
    void fractionalRippleKeepsOtherTracksRoutesRelationshipsAndInlineEffects() {
        var base = document();
        var plan = plan(base);
        var result = plan.candidateTimeline();
        assertSame(base.getTracks().get(1), result.getTracks().get(1));
        assertEquals(base.getSemanticRelationships(), result.getSemanticRelationships());
        assertEquals(base.getAudioMix().routes().subList(1, 2), result.getAudioMix().routes());
        var video = result.getTracks().getFirst().clips();
        assertEquals(List.of("a", "c", "d"), video.stream().map(clip -> clip.getClipId().value()).toList());
        assertEquals(List.of(time("0"), time("2"), time("5")),
                video.stream().map(TimelineClip::getStartTime).toList());
        assertEquals(List.of(time("2"), time("4"), time("6")),
                video.stream().map(TimelineClip::getEndTime).toList());
        var c = video.get(1);
        var original = base.getTracks().getFirst().clips().get(2);
        assertEquals(original.getEffects(), c.getEffects());
        assertEquals(original.getArtifactId(), c.getArtifactId());
        assertEquals(original.getContentDigest(), c.getContentDigest());
        assertEquals(original.getMediaStreamId(), c.getMediaStreamId());
        assertEquals(original.getTrimEnd(), c.getTrimEnd());
        assertEquals(original.getTemporalMapping(), c.getTemporalMapping());
        assertEquals(plan.planDigest(), plan(base).planDigest());
    }

    @Test
    void rejectsAutomationOnLaterClipWithoutMutatingBase() {
        var base = document();
        var automated = new TimelineDocument(base.getSchemaVersion(), base.getTracks(), base.getMetadata(),
                base.getAudioMix(), base.getSemanticRelationships(), List.of(), List.of(),
                List.of(new CanonicalAutomationCurve("curve", "fx-c", "brightness", "NUMBER", "HOLD", List.of())));
        String fingerprint = digester.digest(automated);
        var error = assertThrows(PlanException.class, () -> plan(automated));
        assertTrue(error.getMessage().contains("automation for clip c"), error.getMessage());
        assertEquals(fingerprint, digester.digest(automated));
    }

    private OperationPlan plan(TimelineDocument base) {
        var def = OperationDefinition.V3.RIPPLE_DELETE;
        String hash = digester.digest(base);
        var target = new OperationTargetRequest.ClipSelectionTargetRequest("project",
                new SelectionSpec.ExplicitObjectSelection(List.of(TimelineClipId.of("b"))),
                SelectionSpec.ExpansionPolicy.EXACT);
        var request = new OperationRequest(def.definitionId(), def.version(), target,
                new OperationParameters.NoParameters(), "base", hash, null);
        return new OperationPlanner().plan(OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext("base", hash, base, "project")), "base", base);
    }

    private static TimelineDocument document() {
        var video = new TimelineTrack("video", "Video", TrackType.VIDEO, List.of(
                clip("a", "0", "2"), clip("b", "2", "121/30"),
                clip("c", "121/30", "181/30"), clip("d", "211/30", "241/30")));
        var audio = new TimelineTrack("audio", "Audio", TrackType.AUDIO, List.of(clip("e", "0", "2")));
        var unrelated = SyncRelationship.of(TimelineClipId.of("a"), time("1"), TimelineClipId.of("e"), time("1"));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(video, audio),
                TimelineMetadata.empty(), new AudioMix(AudioMasterBus.master(), List.of(
                        AudioRoute.of(AudioMixInput.of("video", "b")),
                        AudioRoute.of(AudioMixInput.of("video", "c"), new AudioGain(0.5)))), List.of(unrelated));
    }

    private static TimelineClip clip(String id, String start, String end) {
        var effects = "c".equals(id) ? List.of(new TimelineClipEffect("fx-c", BasicVideoEffect.EFFECT_KEY,
                new BasicVideoEffect(0.1, 1, 1, false, 0, 0, true).toParameters())) : List.<TimelineClipEffect>of();
        return new TimelineClip(id, "asset-" + id, "stream-" + id, "artifact-" + id, "a".repeat(64),
                time(start), time(end), MediaTime.ZERO, time(end).subtract(time(start)), "MEDIA_STREAM",
                ConstantRateTemporalMapping.identity(), effects, VideoTransform.IDENTITY);
    }

    private static MediaTime time(String value) {
        return MediaTime.parse(value.contains("/") ? value : value + "/1");
    }
}
