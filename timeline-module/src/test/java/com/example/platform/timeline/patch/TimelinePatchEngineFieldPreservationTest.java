package com.example.platform.timeline.patch;

import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TestTextElements;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.testsupport.CanonicalFieldsAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIELD-PRESERVATION-P0 (Sink 6): {@link TimelinePatchEngine#apply} must preserve the
 * canonical fields it does not touch. This asserts field-by-field on the production
 * document sink — never via the sibling snapshot applier.
 */
class TimelinePatchEngineFieldPreservationTest {

    private static final String PRODUCT_ID = "prod-fp";
    private static final String BASE_REV = "rev-base";
    private static final String BASE_DIGEST = "digest-base";

    @Test
    void updateTrackPropertyPreservesUntouchedCanonicalFields() {
        TimelineDocument base = richDocument();
        TimelinePatch patch = patch(new TimelinePatchOperation.UpdateTrackProperty(
                "op-1", "track-1", "name", "Main", "Main Renamed"));

        TimelineDocument result = apply(base, patch);

        CanonicalFieldsAssert.assertPreserved(base, result, CanonicalFieldsAssert.Field.TRACKS);
        assertEquals("Main Renamed", result.getTracks().get(0).name(), "tracks actually changed");
        assertEquals("Main", base.getTracks().get(0).name(), "base not mutated");
    }

    @Test
    void addTrackPreservesUntouchedCanonicalFields() {
        TimelineDocument base = richDocument();
        TimelinePatch patch = patch(new TimelinePatchOperation.AddTrack(
                "op-1", new TimelineTrack("track-2", "Second", TrackType.VIDEO, List.of()), 1));

        TimelineDocument result = apply(base, patch);

        CanonicalFieldsAssert.assertPreserved(base, result, CanonicalFieldsAssert.Field.TRACKS);
        assertEquals(2, result.getTracks().size());
    }

    @Test
    void removeClipPreservesAllNonTrackFields() {
        TimelineDocument base = richDocument();
        // clip-3 is authored but not referenced by any relationship/transition, so only
        // tracks change.
        TimelinePatch patch = patch(new TimelinePatchOperation.RemoveClip(
                "op-1", "clip-3", "track-1"));

        TimelineDocument result = apply(base, patch);

        CanonicalFieldsAssert.assertPreserved(base, result, CanonicalFieldsAssert.Field.TRACKS);
        assertEquals(2, result.getTracks().get(0).clips().size());
    }

    private static TimelineDocument apply(TimelineDocument base, TimelinePatch patch) {
        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);
        assertTrue(result.isSuccess(),
                () -> "engine must succeed but returned: " + result);
        return ((PatchApplicationResult.Success) result).document();
    }

    private static TimelinePatch patch(TimelinePatchOperation op) {
        return new TimelinePatch("1.0", "patch-fp", PRODUCT_ID, BASE_REV, BASE_DIGEST, BASE_REV,
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(op), null, null);
    }

    private static TimelineDocument richDocument() {
        TimelineClip c1 = clip("clip-1", 0, 1000);
        TimelineClip c2 = clip("clip-2", 1000, 2000);
        TimelineClip c3 = clip("clip-3", 2000, 3000);
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(c1, c2, c3));

        TimelineMetadata metadata = new TimelineMetadata("Title", "Desc", Map.of("k", "v"));
        AudioMix audioMix = AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("track-1", "clip-1"))));
        List<SemanticRelationship> relationships = List.of(
                SyncRelationship.of(TimelineClipId.of("clip-1"), MediaTime.ZERO,
                        TimelineClipId.of("clip-2"), MediaTime.ZERO),
                GroupRelationship.of(GroupId.of("g-1"),
                        List.of(TimelineClipId.of("clip-1"), TimelineClipId.of("clip-2"))));
        List<TextElement> textElements = List.of(TestTextElements.textElement("elem-1"));
        List<CanonicalTransition> transitions = List.of(new CanonicalTransition(
                "transition-1", "crossfade", "1", "clip-1", "clip-2", "VIDEO",
                MediaTime.ofMillis(250), "CENTER", "OVERLAP", Map.of("curve", "linear")));
        List<CanonicalAutomationCurve> automations = List.of(new CanonicalAutomationCurve(
                "automation-1", "clip-1", "parameters.gain", "NUMBER", "HOLD",
                List.of(new CanonicalAutomationKeyframe("kf-1", MediaTime.ZERO, 0.5, "LINEAR"))));

        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track),
                metadata, audioMix, relationships, textElements, transitions, automations);
    }

    private static TimelineClip clip(String id, long startMillis, long endMillis) {
        return new TimelineClip(id, "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis),
                MediaTime.ZERO, MediaTime.ofMillis(endMillis - startMillis), "MEDIA_STREAM");
    }
}
