package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * GCR-1 CORRECTION V2: timeline-owned canonical validation authority tests
 * (moved from the render-side TimelineValidationServiceTest — validation now
 * lives in timeline-module and is the sole production canonical validator).
 */
class InternalTimelineValidationServiceTest {

    private final InternalTimelineValidationService service = new InternalTimelineValidationService();

    @Test
    void rejectsEmptyJson() {
        assertFalse(service.validate("").valid());
    }

    @Test
    void validatesV1Sample() throws Exception {
        String json = Files.readString(FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json"));
        assertTrue(service.validate(json).valid());
    }

    @Test
    void rejectsLegacyTracksRoot() {
        String json = """
                {"id":"tl-1","tracks":[{"type":"VIDEO","clips":[]}],"outputSpec":{"format":"mp4"}}
                """;
        assertFalse(service.validate(json).valid());
    }
}
