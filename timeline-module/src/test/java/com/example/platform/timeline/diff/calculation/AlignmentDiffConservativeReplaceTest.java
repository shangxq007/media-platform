package com.example.platform.timeline.diff.calculation;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.AlignmentRelationship;
import com.example.platform.timeline.semantics.relationship.SourceBaseline;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L5-V2 Gate 5: ALIGNMENT same-identity content changes take the conservative
 * remove+add replace path (typed alignment delta semantics deferred to backlog).
 */
class AlignmentDiffConservativeReplaceTest {

    private final CanonicalTimelineDiffCalculator calculator = new CanonicalTimelineDiffCalculator();

    @Test
    void alignmentEndpointChangeIsConservativeReplace() {
        var before = TimelineSnapshotConverter.toSnapshot(doc(Set.of("d1")), "rev-1");
        var after = TimelineSnapshotConverter.toSnapshot(doc(Set.of("d1", "d2")), "rev-2");

        var result = calculator.calculate(before, after);

        assertTrue(result.successful());
        assertFalse(result.diff().isNoOp());
        assertTrue(hasType(result, TimelineChangeType.RELATIONSHIP_REMOVED),
                "conservative replace must emit RELATIONSHIP_REMOVED");
        assertTrue(hasType(result, TimelineChangeType.RELATIONSHIP_ADDED),
                "conservative replace must emit RELATIONSHIP_ADDED");
    }

    @Test
    void identicalAlignmentIsNoOp() {
        var before = TimelineSnapshotConverter.toSnapshot(doc(Set.of("d1")), "rev-1");
        var after = TimelineSnapshotConverter.toSnapshot(doc(Set.of("d1")), "rev-2");

        var result = calculator.calculate(before, after);

        assertFalse(hasType(result, TimelineChangeType.RELATIONSHIP_REMOVED));
        assertFalse(hasType(result, TimelineChangeType.RELATIONSHIP_ADDED));
    }

    private static boolean hasType(CanonicalTimelineDiffCalculationResult result, TimelineChangeType type) {
        return result.diff().operations().stream().anyMatch(op -> op.type() == type);
    }

    private static TimelineDocument doc(Set<String> derived) {
        TimelineClip clip = new TimelineClip("c1", "asset-1", null, null, null,
                MediaTime.ZERO, MediaTime.ofMillis(1000), MediaTime.ZERO, MediaTime.ofMillis(1000), "MEDIA_STREAM");
        Set<SourceUnitId> d = new java.util.TreeSet<>();
        derived.forEach(v -> d.add(SourceUnitId.of(v)));
        AlignmentRelationship rel = new AlignmentRelationship(AlignmentId.of("align-1"),
                Set.of(SourceUnitId.of("s1")), d, new SourceBaseline("rev-1", "digest-1"), List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "Main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(rel));
    }
}
