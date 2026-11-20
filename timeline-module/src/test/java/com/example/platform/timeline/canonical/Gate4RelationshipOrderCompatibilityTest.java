package com.example.platform.timeline.canonical;

import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.RelationshipCanonicalSemantics;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FIELD/L5-V2 Gate 4 (AC-COMPAT): delegating {@code RELATIONSHIP_ORDER} to
 * {@link RelationshipCanonicalSemantics#canonicalKey} must NOT change the canonical serialization
 * bytes or content hash of any document that contains only the pre-existing relationship kinds
 * (GROUP / SYNC). The expected digests and relationship orderings below were recorded BEFORE the
 * Gate 4 change against commit a65b0b9f.
 *
 * <p>{@code TimelineContentDigester.digest(doc)} is SHA-256 over
 * {@code TimelineDocumentJsonSerializer.serialize(doc)} bytes, so digest equality is byte equality.
 */
class Gate4RelationshipOrderCompatibilityTest {

    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    // Recorded pre-change (commit a65b0b9f) — must be byte-identical after Gate 4.
    private static final String SAMPLE_A_DIGEST = "yjuY7bSxHS9DsutH80P7J5MMj2yjL489COSL/bxsqsw=";
    private static final String SAMPLE_B_DIGEST = "JWM38i1Nk7Rir3JBb4COSGKHGcZ1p5XBhx/fNlK2NCc=";
    private static final String SAMPLE_C_DIGEST = "hTI0FoJla+8hq2JalECvIfzalzwlCH+wQAFSOBs+D9o=";

    @Test
    void sampleAGroupAndSyncDigestUnchanged() {
        TimelineDocument doc = sampleA();
        assertEquals(SAMPLE_A_DIGEST, DIGESTER.digest(doc), "sample A content hash");
        assertEquals(List.of("group:g1", "sync:c1:c2"), keys(doc), "sample A ordering");
    }

    @Test
    void sampleBMultipleRelationshipsDigestUnchanged() {
        TimelineDocument doc = sampleB();
        assertEquals(SAMPLE_B_DIGEST, DIGESTER.digest(doc), "sample B content hash");
        assertEquals(List.of("group:g1", "group:g2", "sync:c1:c2", "sync:c1:c3"), keys(doc),
                "sample B ordering");
    }

    @Test
    void sampleCReorderedInputDigestUnchanged() {
        TimelineDocument doc = sampleC();
        assertEquals(SAMPLE_C_DIGEST, DIGESTER.digest(doc), "sample C content hash");
    }

    private static List<String> keys(TimelineDocument doc) {
        return doc.getSemanticRelationships().stream()
                .map(RelationshipCanonicalSemantics::canonicalKey)
                .toList();
    }

    private static TimelineDocument sampleA() {
        return document(
                List.of(clip("c1", 0, 1000), clip("c2", 1000, 2000)),
                List.of(
                        // deliberately unsorted union / reversed sync endpoints
                        sync("c2", "c1"),
                        group("g1", "c2", "c1")));
    }

    private static TimelineDocument sampleB() {
        return document(
                List.of(clip("c1", 0, 1000), clip("c2", 1000, 2000), clip("c3", 2000, 3000)),
                List.of(
                        sync("c1", "c3"), group("g2", "c2", "c3"),
                        sync("c2", "c1"), group("g1", "c1", "c2")));
    }

    private static TimelineDocument sampleC() {
        return document(
                List.of(clip("cx", 0, 500), clip("cy", 500, 1500)),
                List.of(
                        group("gz", "cy", "cx"),
                        sync("cy", "cx")));
    }

    private static TimelineDocument document(List<TimelineClip> clips,
                                             List<SemanticRelationship> rels) {
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, clips);
        AudioMix audioMix = AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("track-1", clips.getFirst().getClipId().value()))));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track),
                new TimelineMetadata("Title", "Desc", Map.of("k", "v")), audioMix, rels);
    }

    private static SyncRelationship sync(String a, String b) {
        return SyncRelationship.of(TimelineClipId.of(a), MediaTime.ZERO, TimelineClipId.of(b), MediaTime.ZERO);
    }

    private static GroupRelationship group(String id, String... members) {
        return GroupRelationship.of(GroupId.of(id),
                java.util.Arrays.stream(members).map(TimelineClipId::of).toList());
    }

    private static TimelineClip clip(String id, long startMillis, long endMillis) {
        return new TimelineClip(id, "asset-1", null, null, null,
                MediaTime.ofMillis(startMillis), MediaTime.ofMillis(endMillis),
                MediaTime.ZERO, MediaTime.ofMillis(endMillis - startMillis), "MEDIA_STREAM");
    }
}
