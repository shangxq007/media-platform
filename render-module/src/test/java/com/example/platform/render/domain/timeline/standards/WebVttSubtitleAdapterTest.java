package com.example.platform.render.domain.timeline.standards;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebVttSubtitleAdapterTest {

    @Test
    void parseSimpleCues() {
        String vtt = "WEBVTT\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Hello World\n\n"
                + "00:00:05.000 --> 00:00:08.000\n"
                + "Second cue\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(2, overlays.size());
        assertEquals("Hello World", overlays.get(0).text());
        assertEquals(1.0, overlays.get(0).startTime(), 0.001);
        assertEquals(3.0, overlays.get(0).duration(), 0.001);
        assertEquals("Second cue", overlays.get(1).text());
        assertEquals(5.0, overlays.get(1).startTime(), 0.001);
    }

    @Test
    void parseCueWithCueId() {
        String vtt = "WEBVTT\n\n"
                + "cue-1\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Text with ID\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertEquals("Text with ID", overlays.get(0).text());
    }

    @Test
    void parseMultiLineCue() {
        String vtt = "WEBVTT\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Line one\n"
                + "Line two\n"
                + "Line three\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertTrue(overlays.get(0).text().contains("Line one"));
        assertTrue(overlays.get(0).text().contains("Line two"));
    }

    @Test
    void parseWithBom() {
        String vtt = "\uFEFFWEBVTT\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "BOM test\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertEquals("BOM test", overlays.get(0).text());
    }

    @Test
    void parseNullReturnsEmpty() {
        assertTrue(WebVttSubtitleAdapter.parse(null).isEmpty());
    }

    @Test
    void parseBlankReturnsEmpty() {
        assertTrue(WebVttSubtitleAdapter.parse("").isEmpty());
        assertTrue(WebVttSubtitleAdapter.parse("   ").isEmpty());
    }

    @Test
    void parseMalformedTimingSkipsCue() {
        String vtt = "WEBVTT\n\n"
                + "invalid timing\n"
                + "This should be skipped\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Valid cue\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertEquals("Valid cue", overlays.get(0).text());
    }

    @Test
    void parseSkipsNoteBlocks() {
        String vtt = "WEBVTT\n\n"
                + "NOTE\n"
                + "This is a comment\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "Real cue\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertEquals("Real cue", overlays.get(0).text());
    }

    @Test
    void parseHtmlLikeTextPreserved() {
        String vtt = "WEBVTT\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "<b>Bold</b> and <i>italic</i>\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertTrue(overlays.get(0).text().contains("<b>Bold</b>"));
    }

    @Test
    void exportRoundtrip() {
        List<TimelineTextOverlay> input = List.of(
                TimelineTextOverlay.of("1", "Hello", 1.0, 3.0),
                TimelineTextOverlay.of("2", "World", 5.0, 3.0)
        );

        String exported = WebVttSubtitleAdapter.toWebVtt(input, "en");

        assertTrue(exported.startsWith("WEBVTT"));
        assertTrue(exported.contains("Language: en"));
        assertTrue(exported.contains("Hello"));
        assertTrue(exported.contains("World"));

        // Re-parse
        List<TimelineTextOverlay> reparsed = WebVttSubtitleAdapter.parse(exported);
        assertEquals(2, reparsed.size());
        assertEquals("Hello", reparsed.get(0).text());
        assertEquals("World", reparsed.get(1).text());
    }

    @Test
    void exportWithoutLanguage() {
        List<TimelineTextOverlay> input = List.of(
                TimelineTextOverlay.of("1", "Test", 0.0, 2.0)
        );

        String exported = WebVttSubtitleAdapter.toWebVtt(input, null);

        assertTrue(exported.startsWith("WEBVTT"));
        assertFalse(exported.contains("Language:"));
    }

    @Test
    void parseChineseCues() {
        String vtt = "WEBVTT\n\n"
                + "00:00:01.000 --> 00:00:04.000\n"
                + "你好世界\n";

        List<TimelineTextOverlay> overlays = WebVttSubtitleAdapter.parse(vtt);

        assertEquals(1, overlays.size());
        assertEquals("你好世界", overlays.get(0).text());
    }
}
