package com.example.platform.web.render;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FIELD-PRESERVATION-P0 (Q2): {@link TimelineGitV1Controller.SaveRevisionRequest} is an
 * intentional HTTP API projection, not a full {@code TimelineDocument} surface. These tests
 * pin that contract so future DTO drift cannot silently introduce base-inheritance
 * expectations onto a tracks-only projection.
 */
class SaveRevisionRequestContractTest {

    private static TimelineDocument toDocument() {
        var clip = new TimelineGitV1Controller.ClipDto("clip-1", "asset-1", 0L, 1000L);
        var track = new TimelineGitV1Controller.TrackDto("track-1", "Main", "VIDEO", List.of(clip));
        var request = new TimelineGitV1Controller.SaveRevisionRequest(null, List.of(track));
        return request.toDocument();
    }

    @Test
    void toDocumentProducesEmptyNonTrackFields() {
        TimelineDocument document = toDocument();

        assertEquals(TimelineDocument.CURRENT_SCHEMA_VERSION, document.getSchemaVersion());
        assertEquals(1, document.getTracks().size(), "tracks is the carried projection field");
        assertEquals(AudioMix.EMPTY, document.getAudioMix(), "audioMix materialized as empty");
        assertTrue(document.getSemanticRelationships().isEmpty(), "semanticRelationships empty");
        assertTrue(document.getTextElements().isEmpty(), "textElements empty");
        assertTrue(document.getTransitions().isEmpty(), "transitions empty");
        assertTrue(document.getAutomations().isEmpty(), "automations empty");
        assertEquals(TimelineMetadata.empty(), document.getMetadata(), "metadata materialized as empty");
    }

    @Test
    void toDocumentDoesNotInheritFromBase() {
        // Compile-time / DTO-shape fact: SaveRevisionRequest has no base-document parameter,
        // so it cannot inherit from or preserve any base revision's fields.
        RecordComponent[] components =
                TimelineGitV1Controller.SaveRevisionRequest.class.getRecordComponents();
        assertEquals(2, components.length, "DTO intentionally carries exactly two components");
        assertEquals("expectedCurrentRevisionId", components[0].getName());
        assertEquals("tracks", components[1].getName());
    }
}
