package com.example.platform.timeline.semantics.relationship;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** L5-V2 Gate 5: alignment serialization round-trip + document digest stability. */
class AlignmentSerializationTest {

    private static AlignmentRelationship alignment() {
        return new AlignmentRelationship(
                AlignmentId.of("align-1"),
                Set.of(SourceUnitId.of("s2"), SourceUnitId.of("s1")),
                Set.of(SourceUnitId.of("d1")),
                new SourceBaseline("rev-1", "digest-1"),
                List.of(new AlignmentAttribute("lang", new AlignmentAttributeValue.TextValue("zh-CN")),
                        new AlignmentAttribute("priority", new AlignmentAttributeValue.NumberValue(2L))));
    }

    @Test
    void canonicalJsonRoundTripIsLossless() {
        AlignmentRelationship original = alignment();
        String json = RelationshipCanonicalSemantics.canonicalJson(original);
        SemanticRelationship decoded = RelationshipCanonicalSemantics.fromCanonicalJson(json);

        assertEquals(original, decoded);
        assertEquals(SemanticRelationship.Kind.ALIGNMENT, decoded.kind());
    }

    @Test
    void documentRoundTripAndDigestStable() {
        TimelineClip clip = new TimelineClip("c1", "asset-1", null, null, null,
                MediaTime.ZERO, MediaTime.ofMillis(1000), MediaTime.ZERO, MediaTime.ofMillis(1000), "MEDIA_STREAM");
        TimelineDocument doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "Main", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(), AudioMix.EMPTY, List.of(alignment()));

        String serialized = TimelineDocumentJsonSerializer.serialize(doc);
        TimelineDocument decoded = TimelineDocumentJsonSerializer.deserialize(serialized);

        assertEquals(doc.getSemanticRelationships(), decoded.getSemanticRelationships());
        assertEquals(serialized, TimelineDocumentJsonSerializer.serialize(decoded),
                "re-serialization must be byte-identical");
        assertEquals(new TimelineContentDigester().digest(doc), new TimelineContentDigester().digest(decoded),
                "content hash must be stable across round-trip");
    }

    @Test
    void attributesAreSortedByTagForDeterminism() {
        AlignmentRelationship rel = new AlignmentRelationship(AlignmentId.of("a"),
                Set.of(SourceUnitId.of("s1")), Set.of(SourceUnitId.of("d1")),
                new SourceBaseline("r", "d"),
                List.of(new AlignmentAttribute("zeta", new AlignmentAttributeValue.TextValue("z")),
                        new AlignmentAttribute("alpha", new AlignmentAttributeValue.BooleanValue(true))));
        assertEquals(List.of("alpha", "zeta"), rel.attributes().stream().map(AlignmentAttribute::tag).toList());
    }
}
