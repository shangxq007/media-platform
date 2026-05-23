package com.example.platform.render.domain.timeline.standards;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SrtSubtitleAdapterTest {

    @Test
    void parsesBasicSrt() {
        String srt = """
                1
                00:00:01,000 --> 00:00:03,500
                Hello world

                2
                00:00:04,000 --> 00:00:06,000
                Second line
                """;
        var overlays = SrtSubtitleAdapter.parse(srt);
        assertEquals(2, overlays.size());
        assertEquals("Hello world", overlays.get(0).text());
        assertEquals(1.0, overlays.get(0).startTime(), 0.01);
    }
}
