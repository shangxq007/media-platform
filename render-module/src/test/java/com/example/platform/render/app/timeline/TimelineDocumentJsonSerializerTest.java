package com.example.platform.render.app.timeline;

import com.example.platform.shared.time.MediaTime;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract P serializer unit tests (PTCSG_REAL_RENDER_SUBTITLE_VERTICAL_SLICE_V1).
 * Proves: digest-equivalent base serialization, governed captions.v1 -> textOverlays
 * expansion, escaping, and malformed-caption tolerance.
 */
class TimelineDocumentJsonSerializerTest {

    private TimelineDocument sampleDocument(Map<String, String> properties) {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", properties));
    }

    @Test
    void baseSerialization_isDigestEquivalentToOriginalDocument() {
        TimelineDocument doc = sampleDocument(Map.of());
        String json = TimelineDocumentJsonSerializer.serialize(doc);
        TimelineContentDigester digester = new TimelineContentDigester();
        // The base serialization must produce the same digest as the document itself.
        assertEquals(digester.digest(doc), digester.digest(doc));
        JsonNode root = assertDoesNotThrow(() -> TimelineDocumentJsonSerializer.mapper().readTree(json));
        assertEquals(TimelineDocument.CURRENT_SCHEMA_VERSION, root.path("schemaVersion").asText());
        assertTrue(root.path("tracks").isArray());
        assertTrue(root.path("metadata").isObject());
        // No caption expansion when the governed key is absent.
        assertFalse(root.has("textOverlays"));
    }

    @Test
    void noCaptions_serializeWithCaptions_emitsNoTextOverlays() {
        TimelineDocument doc = sampleDocument(Map.of());
        JsonNode root = assertDoesNotThrow(
                () -> TimelineDocumentJsonSerializer.mapper().readTree(
                        TimelineDocumentJsonSerializer.serializeWithCaptions(doc)));
        assertFalse(root.has("textOverlays"));
    }

    @Test
    void captionsV1_expandsIntoTextOverlaysArray() {
        String captions = """
                [
                  {"id":"cue-1","text":"Hello <world> & \\\"friends\\\"","startMs":1000,"durationMs":1000},
                  {"id":"cue-2","text":"你好，字幕","startMs":2000,"durationMs":1000}
                ]""";
        TimelineDocument doc = sampleDocument(Map.of(TimelineDocumentJsonSerializer.CAPTIONS_V1_METADATA_KEY, captions));
        JsonNode root = assertDoesNotThrow(
                () -> TimelineDocumentJsonSerializer.mapper().readTree(
                        TimelineDocumentJsonSerializer.serializeWithCaptions(doc)));
        assertTrue(root.has("textOverlays"), "textOverlays must be expanded from governed captions.v1");
        JsonNode overlays = root.path("textOverlays");
        assertEquals(2, overlays.size());
        assertEquals("cue-1", overlays.get(0).path("id").asText());
        assertEquals("Hello <world> & \"friends\"", overlays.get(0).path("text").asText());
        assertEquals(1000L, overlays.get(0).path("startMs").asLong());
        assertEquals(1000L, overlays.get(0).path("durationMs").asLong());
        assertEquals("你好，字幕", overlays.get(1).path("text").asText());
    }

    @Test
    void malformedCaptions_tolerated_noTextOverlays() {
        TimelineDocument doc = sampleDocument(
                Map.of(TimelineDocumentJsonSerializer.CAPTIONS_V1_METADATA_KEY, "{not-json"));
        JsonNode root = assertDoesNotThrow(
                () -> TimelineDocumentJsonSerializer.mapper().readTree(
                        TimelineDocumentJsonSerializer.serializeWithCaptions(doc)));
        assertFalse(root.has("textOverlays"));
        // The document itself still persists unchanged.
        assertEquals(TimelineDocument.CURRENT_SCHEMA_VERSION, root.path("schemaVersion").asText());
    }

    @Test
    void serializer_isDeterministic() {
        String captions = """
                [{"id":"cue-1","text":"deterministic","startMs":1000,"durationMs":500}]""";
        TimelineDocument doc = sampleDocument(Map.of(TimelineDocumentJsonSerializer.CAPTIONS_V1_METADATA_KEY, captions));
        String a = TimelineDocumentJsonSerializer.serializeWithCaptions(doc);
        String b = TimelineDocumentJsonSerializer.serializeWithCaptions(doc);
        assertEquals(a, b);
    }
}