package com.example.platform.render.domain.timeline;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OpenTimelineioAdapterTest {

    @Test
    void shouldThrowOnExport() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);

        assertThrows(UnsupportedOperationException.class,
                () -> OpenTimelineioAdapter.toOtioJson(timeline));
    }

    @Test
    void shouldThrowOnImport() {
        assertThrows(UnsupportedOperationException.class,
                () -> OpenTimelineioAdapter.fromOtioJson("{}"));
    }
}
