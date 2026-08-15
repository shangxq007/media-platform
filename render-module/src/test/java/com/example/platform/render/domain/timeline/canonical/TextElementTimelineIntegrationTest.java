package com.example.platform.render.domain.timeline.canonical;

import com.example.platform.audio.domain.mix.AudioMix;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROADMAP_19 (C54/C86): pre-#19 Timeline bytes/hash stability + TextElement hashing. */
class TextElementTimelineIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TimelineDocument plainDocument() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of());
    }

    @Test
    void preexistingTimelineSerializationStable() throws Exception {
        TimelineDocument doc = plainDocument();
        String json = MAPPER.writeValueAsString(doc);
        assertFalse(json.contains("textElements"),
                "pre-#19 document must not gain an empty textElements key: " + json);
        // re-serialize deterministically
        assertEquals(json, MAPPER.writeValueAsString(doc));
    }

    @Test
    void textElementChangesDocumentHash() throws Exception {
        TimelineDocument plain = plainDocument();
        String plainJson = MAPPER.writeValueAsString(plain);
        TimelineContentDigester digester = new TimelineContentDigester();
        String plainHash = digester.digest(plain);

        TextElement element = TestTextElements.sampleTextElement();
        TimelineDocument withText = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(element));
        String withTextHash = digester.digest(withText);
        assertNotEquals(plainHash, withTextHash, "authored TextElement must affect Timeline hash (FTG19)");
        assertTrue(MAPPER.writeValueAsString(withText).contains("textElements"));

        // deterministic: same element -> same hash
        TimelineDocument withTextAgain = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(element));
        assertEquals(withTextHash, digester.digest(withTextAgain));
    }

    @Test
    void textElementOrderingDeterministic() throws Exception {
        TextElement a = TestTextElements.textElement("elem-a");
        TextElement b = TestTextElements.textElement("elem-b");
        TimelineDocument d1 = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(),
                List.of(b, a)); // deliberately reversed input
        TimelineDocument d2 = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(),
                List.of(a, b));
        TimelineContentDigester digester = new TimelineContentDigester();
        assertEquals(digester.digest(d1), digester.digest(d2),
                "TextElement compositing order must be deterministic by id");
    }
}
