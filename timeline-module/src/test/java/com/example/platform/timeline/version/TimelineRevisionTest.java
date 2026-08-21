package com.example.platform.timeline.version;

import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionTest {

    /** Clean-forward canonical fixture: revision owns authoritative EMPTY Effect semantics. */
    private static TimelineRevision revision(String id, String product, String parent) {
        TimelineDocument doc = ownDocument();
        String digest = new com.example.platform.timeline.canonical.TimelineContentDigester().digest(doc);
        EffectSemanticSnapshot empty = EffectSemanticSnapshotFixture.emptySnapshot();
        String revDigest = com.example.platform.timeline.semantics.effect
                .TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(
                        digest, empty.reference());
        return new TimelineRevision(
                id, product, parent, "timeline-1.0",
                doc, revDigest, Instant.now(), "user-1",
                new TimelineRevisionSemanticContext(
                        digest, empty.reference(), revDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    /** Canonical empty document fixture (deterministic digest). */
    private static TimelineDocument ownDocument() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), com.example.platform.timeline.canonical.TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of());
    }

    @Test
    void rootRevision_hasNullParent() {
        var revision = revision("rev-1", "prod-1", null);

        assertTrue(revision.isRoot());
        assertNull(revision.parentRevisionId());
    }

    @Test
    void childRevision_hasParent() {
        var revision = revision("rev-2", "prod-1", "rev-1");

        assertFalse(revision.isRoot());
        assertEquals("rev-1", revision.parentRevisionId());
    }

    @Test
    void nullRevisionId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                revision(null, "prod-1", null));
    }

    @Test
    void nullProductId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                revision("rev-1", null, null));
    }

    @Test
    void missingSemanticContext_rejectedCleanForward() {
        // CF1: a valid canonical revision CANNOT be created without semantic
        // context — no compatibility constructor exists.
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRevision(
                        "rev-1", "prod-1", null, "timeline-1.0",
                        null, "digest", Instant.now(), "user-1", null));
    }

    @Test
    void digestMismatchWithContext_rejected() {
        // REVISION DIGEST MISMATCH: contentDigest must equal the context's
        // revision semantic digest.
        EffectSemanticSnapshot empty = EffectSemanticSnapshotFixture.emptySnapshot();
        TimelineRevisionSemanticContext ctx = new TimelineRevisionSemanticContext(
                "tl-digest", empty.reference(), "rev-digest",
                TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRevision(
                        "rev-1", "prod-1", null, "timeline-1.0",
                        null, "different-digest", Instant.now(), "user-1", ctx));
    }

    @Test
    void hydrate_acceptsOwnPersistedDocument() {
        // §15: hydrate() accepts the revision's OWN persisted document (its
        // Timeline digest matches the semantic context's committed digest).
        var original = revision("rev-1", "prod-1", null);
        TimelineDocument ownDoc = original.canonicalTimeline();
        var hydrated = original.hydrate(ownDoc);
        assertEquals("rev-1", hydrated.revisionId());
        assertEquals(original.effectSemanticSnapshotReference(),
                hydrated.effectSemanticSnapshotReference());
        assertEquals(original.contentDigest(), hydrated.contentDigest());
    }

    @Test
    void hydrate_rejectsForeignDocumentFailClosed() {
        // §15: a document whose Timeline digest does NOT match the context's
        // committed Timeline digest FAILS CLOSED — no caller can mutate
        // canonical content through hydration.
        var original = revision("rev-1", "prod-1", null);
        var foreign = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new com.example.platform.timeline.canonical.TimelineTrack(
                        "t-x", "v-x", com.example.platform.timeline.canonical.TrackType.VIDEO, List.of())),
                com.example.platform.timeline.canonical.TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> original.hydrate(foreign),
                "TIMELINE_REVISION_HYDRATION_DIGEST_MISMATCH_V1");
    }

    @Test
    void schemaVersion_preserved() {
        var revision = revision("rev-1", "prod-1", null);
        assertEquals("timeline-1.0", revision.timelineSchemaVersion());
    }

    @Test
    void revisionOwnsExactEffectPin() {
        var revision = revision("rev-1", "prod-1", null);
        assertNotNull(revision.effectSemanticSnapshotReference());
        assertEquals(EffectSemanticContractVersion.current().value(),
                revision.effectSemanticSnapshotReference().semanticContractVersion().value());
    }

    /** Small fixture helper: authoritative EMPTY snapshot via the domain authority. */
    private static final class EffectSemanticSnapshotFixture {
        static EffectSemanticSnapshot emptySnapshot() {
            return new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                    new com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry.InMemory(),
                    new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore.InMemory())
                    .mintEmpty();
        }
    }
}
