package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimelineDocumentSemanticRoundTripTest {

    @Test
    void productionCodecAndMergeBridgePreserveEffectsTransitionsAndAutomation() {
        TimelineClipEffect effect = new TimelineClipEffect(
                "effect-1", "color.grade", Map.of("enabled", true, "strength", 0.75));
        TimelineClip outgoing = clip("clip-out", 0, 1_000, List.of(effect));
        TimelineClip incoming = clip("clip-in", 1_000, 2_000, List.of());
        CanonicalTransition transition = new CanonicalTransition(
                "transition-1", "crossfade", "1", "clip-out", "clip-in", "VIDEO",
                MediaTime.ofMillis(250), "CENTER", "OVERLAP", Map.of("curve", "linear"));
        CanonicalAutomationCurve automation = new CanonicalAutomationCurve(
                "automation-1", "effect-1", "parameters.strength", "NUMBER", "HOLD",
                List.of(
                        new CanonicalAutomationKeyframe(
                                "keyframe-1", MediaTime.ZERO, 0.25, "LINEAR"),
                        new CanonicalAutomationKeyframe(
                                "keyframe-2", MediaTime.ofMillis(500), 0.75, "LINEAR")));
        TimelineDocument original = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Video", TrackType.VIDEO,
                        List.of(outgoing, incoming))),
                TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                List.of(),
                List.of(),
                List.of(transition),
                List.of(automation));

        String persisted = TimelineDocumentJsonSerializer.serialize(original);
        TimelineDocument decoded = TimelineDocumentJsonSerializer.deserialize(persisted);
        TimelineDocument rebuilt = TimelineSnapshotConverter.toDocument(
                TimelineSnapshotConverter.toSnapshot(decoded, "revision-1"));

        assertEquals(persisted, TimelineDocumentJsonSerializer.serialize(decoded));
        assertEquals(persisted, TimelineDocumentJsonSerializer.serialize(rebuilt));
        assertEquals(List.of(effect), rebuilt.getTracks().getFirst().clips().getFirst().getEffects());
        assertEquals(List.of(transition), rebuilt.getTransitions());
        assertEquals(List.of(automation), rebuilt.getAutomations());
    }

    private static TimelineClip clip(
            String id, long startMillis, long endMillis, List<TimelineClipEffect> effects) {
        return new TimelineClip(
                id, "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis),
                MediaTime.ZERO, MediaTime.ofMillis(endMillis - startMillis),
                "MEDIA_STREAM", null, effects);
    }
}
