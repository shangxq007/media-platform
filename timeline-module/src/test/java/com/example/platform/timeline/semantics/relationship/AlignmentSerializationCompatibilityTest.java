package com.example.platform.timeline.semantics.relationship;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * L5-V2 (Phase G): ALIGNMENT participates in the canonical content hash; documents without
 * ALIGNMENT are unaffected; and descriptor ordering does not perturb the digest.
 */
class AlignmentSerializationCompatibilityTest {

    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void alignmentParticipatesInContentHash() {
        assertNotEquals(DIGESTER.digest(document(List.of())), DIGESTER.digest(document(List.of(alignment()))),
                "adding an alignment must change the content hash");
    }

    @Test
    void alignmentDocumentRoundTripIsByteStable() {
        TimelineDocument doc = document(List.of(alignment()));
        String first = TimelineDocumentJsonSerializer.serialize(doc);
        TimelineDocument decoded = TimelineDocumentJsonSerializer.deserialize(first);
        assertEquals(first, TimelineDocumentJsonSerializer.serialize(decoded));
        assertEquals(DIGESTER.digest(doc), DIGESTER.digest(decoded));
    }

    @Test
    void endpointInputOrderDoesNotChangeDigest() {
        Set<SourceUnitId> forward = new LinkedHashSet<>(List.of(SourceUnitId.of("s1"), SourceUnitId.of("s2")));
        Set<SourceUnitId> reverse = new LinkedHashSet<>(List.of(SourceUnitId.of("s2"), SourceUnitId.of("s1")));
        AlignmentRelationship a = new AlignmentRelationship(AlignmentId.of("align-1"), forward,
                Set.of(SourceUnitId.of("d1")), new SourceBaseline("rev-1", "digest-1"), List.of());
        AlignmentRelationship b = new AlignmentRelationship(AlignmentId.of("align-1"), reverse,
                Set.of(SourceUnitId.of("d1")), new SourceBaseline("rev-1", "digest-1"), List.of());
        assertEquals(DIGESTER.digest(document(List.of(a))), DIGESTER.digest(document(List.of(b))));
    }

    private static AlignmentRelationship alignment() {
        return new AlignmentRelationship(AlignmentId.of("align-1"),
                new TreeSet<>(List.of(SourceUnitId.of("s1"))),
                new TreeSet<>(List.of(SourceUnitId.of("d1"))),
                new SourceBaseline("rev-1", "digest-1"), List.of());
    }

    private static TimelineDocument document(List<SemanticRelationship> relationships) {
        TimelineClip clip = new TimelineClip("c1", "asset-1", null, null, null,
                MediaTime.ZERO, MediaTime.ofMillis(1000), MediaTime.ZERO, MediaTime.ofMillis(1000), "MEDIA_STREAM");
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "Main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(), AudioMix.EMPTY, relationships);
    }
}
