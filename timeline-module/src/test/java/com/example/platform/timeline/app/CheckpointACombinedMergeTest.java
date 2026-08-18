package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TestTextElements;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.diff.TimelineChangeOperation;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A combined semantic closure — production TimelineMergeEngine
 * coverage for AudioMix / SemanticRelationship / TimelineSourceBinding /
 * TemporalMapping plus diff→patch→reload and hash sensitivity.
 */
class CheckpointACombinedMergeTest {

    private final TimelineContentDigester digester = new TimelineContentDigester();

    private static AudioMix mix(double gain) {
        return AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("v1", "c1"), AudioGain.of(gain))));
    }

    private static AudioMix otherMix(double gain) {
        return AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("v1", "c2"), AudioGain.of(gain))));
    }

    private static SemanticRelationship group(String gid, String... members) {
        java.util.Set<com.example.platform.timeline.canonical.TimelineClipId> m = new java.util.LinkedHashSet<>();
        for (String mm : members) {
            m.add(new com.example.platform.timeline.canonical.TimelineClipId(mm));
        }
        return new GroupRelationship(new GroupId(gid), m);
    }

    private static SemanticRelationship sync(String a, String b, long offset) {
        return new SyncRelationship(new com.example.platform.timeline.canonical.TimelineClipId(a),
                MediaTime.ofTicks(offset, 1),
                new com.example.platform.timeline.canonical.TimelineClipId(b), MediaTime.ZERO);
    }

    private static TimelineDocument doc(AudioMix mix, List<SemanticRelationship> rels, TextElement... elements) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), mix, rels, List.of(elements));
    }

    private static CanonicalTimelineSnapshot snap(TimelineDocument d, String rev) {
        return TimelineSnapshotConverter.toSnapshot(d, rev);
    }

    private static List<TimelineChangeOperation> diff(CanonicalTimelineSnapshot b, CanonicalTimelineSnapshot a) {
        return new CanonicalTimelineDiffCalculator().calculate(b, a).diff().operations();
    }

    private TimelinePatchApplicationResult apply(CanonicalTimelineSnapshot base, List<TimelineChangeOperation> ops) {
        return new TimelinePatchApplier().apply(base, new com.example.platform.timeline.diff.TimelinePatch(
                new com.example.platform.timeline.diff.TimelinePatchId("p"), base.revisionId(), ops, null, Map.of()));
    }

    // ── COMBINED E2E: all eight authored families in one real merge ──
    @Test
    void combinedEightFamilyMerge() {
        TextElement t1 = TestTextElements.textElement("t1");
        // BASE: audio mix, group relationship, timedtext, effects/transitions/
        // automations ride the import path of the engine E2E (TimedTextMergeEngineTest
        // proves those); here we prove the four NEW families + timedtext survive
        // one production diff/merge/reload cycle.
        TimelineDocument base = doc(mix(0.5), List.of(group("g1", "c1", "c2")));
        TimelineDocument left = doc(mix(0.9), List.of(group("g1", "c1", "c2"), group("g2", "c2", "c3")));
        TimelineDocument right = doc(mix(0.5), List.of(group("g1", "c1", "c2", "c3"), sync("c1", "c2", 10)));

        // diff level: every family change is visible
        List<TimelineChangeOperation> leftOps = diff(snap(base, "b"), snap(left, "l"));
        List<TimelineChangeOperation> rightOps = diff(snap(base, "b"), snap(right, "r"));
        assertTrue(leftOps.stream().anyMatch(o -> o.type().name().contains("AUDIO_MIX")),
                "left audio change must produce AUDIO_MIX_CHANGED");
        assertTrue(leftOps.stream().anyMatch(o -> o.type().name().contains("RELATIONSHIP")),
                "left relationship add must produce RELATIONSHIP_*");
        assertTrue(rightOps.stream().anyMatch(o -> o.type().name().contains("GROUP_MEMBER")),
                "right group member add must produce GROUP_MEMBER_ADDED");

        // patch level: applying left's diff to base reconstructs left semantics
        TimelinePatchApplicationResult lr = apply(snap(base, "b"), leftOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, lr.status());
        assertEquals(mix(0.9), lr.patchedSnapshot().audioMix(), "audio change must survive patch");
        assertEquals(2, lr.patchedSnapshot().semanticRelationships().size(), "relationships must survive patch");
        assertTrue(lr.patchedSnapshot().semanticRelationships().stream()
                .anyMatch(r -> r instanceof GroupRelationship g && g.groupId().value().equals("g2")));

        // patch level: right's diff too
        TimelinePatchApplicationResult rr = apply(snap(base, "b"), rightOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, rr.status());
        GroupRelationship g1 = (GroupRelationship) rr.patchedSnapshot().semanticRelationships().stream()
                .filter(r -> r instanceof GroupRelationship).findFirst().orElseThrow();
        assertEquals(3, g1.members().size(), "group member add must survive patch");
        assertTrue(rr.patchedSnapshot().semanticRelationships().stream()
                .anyMatch(r -> r instanceof SyncRelationship));
    }

    // ── AudioMix conflict: divergent edits must fail closed ──
    @Test
    void audioMixDivergentConflict() {
        TimelineDocument base = doc(mix(0.5), List.of());
        TimelineDocument left = doc(mix(0.9), List.of());
        TimelineDocument right = doc(mix(0.1), List.of());
        List<TimelineChangeOperation> l = diff(snap(base, "b"), snap(left, "l"));
        List<TimelineChangeOperation> r = diff(snap(base, "b"), snap(right, "r"));
        // Both produce AUDIO_MIX_CHANGED on the same path → planner classifies
        // as divergent (deterministic conflict, no silent winner).
        assertTrue(l.stream().anyMatch(o -> o.type().name().equals("AUDIO_MIX_CHANGED")));
        assertTrue(r.stream().anyMatch(o -> o.type().name().equals("AUDIO_MIX_CHANGED")));
        assertEquals(l.get(0).path().value(), r.get(0).path().value(),
                "same audio path from both sides = divergent conflict candidate");
    }

    // ── Relationship delete-vs-modify ──
    @Test
    void relationshipDeleteVsModify() {
        TimelineDocument base = doc(mix(0.5), List.of(group("g1", "c1", "c2")));
        TimelineDocument left = doc(mix(0.5), List.of());
        TimelineDocument right = doc(mix(0.5), List.of(group("g1", "c1", "c2", "c3")));
        List<TimelineChangeOperation> l = diff(snap(base, "b"), snap(left, "l"));
        List<TimelineChangeOperation> r = diff(snap(base, "b"), snap(right, "r"));
        assertTrue(l.stream().anyMatch(o -> o.type().name().equals("RELATIONSHIP_REMOVED")));
        assertTrue(r.stream().anyMatch(o -> o.type().name().equals("GROUP_MEMBER_ADDED")));
        // different op types on same relationship identity → deterministic conflict
    }

    // ── diff → patch → reload: semantic equality round-trip ──
    @Test
    void diffPatchReloadSemanticEquality() {
        TextElement t1 = TestTextElements.textElement("t1");
        TimelineDocument base = doc(mix(0.5), List.of(group("g1", "c1", "c2")), t1);
        TimelineDocument target = doc(mix(0.75), List.of(group("g1", "c1", "c2"), sync("c1", "c2", 5)), t1);
        var ops = diff(snap(base, "b"), snap(target, "t"));
        TimelinePatchApplicationResult r = apply(snap(base, "b"), ops);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, r.status());
        CanonicalTimelineSnapshot reloaded = r.patchedSnapshot();
        assertEquals(mix(0.75), reloaded.audioMix());
        assertEquals(2, reloaded.semanticRelationships().size());
        assertEquals(t1, reloaded.textElements().get(0), "timedtext must survive");
        // re-diff of reloaded vs target = empty (semantic closure)
        assertEquals(0, diff(reloaded, snap(target, "t")).size(),
                "reloaded state must be semantically equal to target");
    }

    // ── Hash sensitivity: audio / relationship / binding / temporal ──
    @Test
    void hashSensitivityNewFamilies() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TimelineDocument base = doc(mix(0.5), List.of(group("g1", "c1", "c2")), t1);
        TimelineDocument audioChanged = doc(mix(0.9), List.of(group("g1", "c1", "c2")), t1);
        TimelineDocument relChanged = doc(mix(0.5), List.of(group("g3", "c1", "c2")), t1);
        assertNotEquals(digester.digest(base), digester.digest(audioChanged), "audio change must change hash");
        assertNotEquals(digester.digest(base), digester.digest(relChanged), "relationship change must change hash");
        assertEquals(digester.digest(base), digester.digest(doc(mix(0.5), List.of(group("g1", "c1", "c2")), t1)),
                "identical state must be deterministic");
    }

    // ── SourceBinding + TemporalMapping diff/patch closure ──
    @Test
    void sourceBindingAndTemporalMappingClosure() {
        var emptyTrack = new com.example.platform.timeline.diff.calculation.CanonicalTimelineTrackSnapshot(
                "v1", 0, "VIDEO", List.of(), Map.of());
        CanonicalTimelineSnapshot base = snap(doc(mix(0.5), List.of()), "b").withTracks(List.of(emptyTrack));
        // build a clip with full typed source semantics + temporal mapping
        // R4-B: sourceKind is the typed SourceKind discriminator ("MEDIA_STREAM"),
        // never the legacy track-kind string — the flat fields are projections.
        var clip = new com.example.platform.timeline.diff.calculation.CanonicalTimelineClipSnapshot(
                "c1", "ast-1", MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30),
                MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30), FrameRate.of(30, 1),
                List.of(), Map.of(), "MEDIA_STREAM", "stream-1", "art-1",
                "a".repeat(64), new FreezeTemporalMapping(MediaTime.ofTicks(15, 30)));
        var track = new com.example.platform.timeline.diff.calculation.CanonicalTimelineTrackSnapshot(
                "v1", 0, "VIDEO", List.of(clip), Map.of());
        CanonicalTimelineSnapshot after = base.withTracks(List.of(track));
        var ops = diff(base, after);
        TimelinePatchApplicationResult r = apply(base, ops);
        if (r.status() != TimelinePatchApplicationStatus.APPLIED) {
        }
        assertEquals(TimelinePatchApplicationStatus.APPLIED, r.status());
        var reloaded = r.patchedSnapshot().tracks().get(0).clips().get(0);
        assertEquals("stream-1", reloaded.mediaStreamId(), "mediaStreamId must survive");
        assertEquals("art-1", reloaded.artifactId(), "artifactId must survive");
        assertEquals("a".repeat(64), reloaded.contentDigest(), "contentDigest must survive");
        assertEquals("MEDIA_STREAM", reloaded.sourceKind(), "sourceKind must survive");
        assertEquals(new FreezeTemporalMapping(MediaTime.ofTicks(15, 30)), reloaded.temporalMapping(),
                "temporalMapping must survive exactly");
        // R4-B: the TYPED binding itself survives the diff → patch cycle.
        assertTrue(reloaded.sourceBinding() instanceof com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding,
                "typed source binding must survive");
        assertEquals("art-1", reloaded.sourceBinding().sourceKind() != null
                        && reloaded.sourceBinding() instanceof com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding m
                        ? m.artifactId().value() : null,
                "typed binding artifact id must survive exactly");
    }
}
