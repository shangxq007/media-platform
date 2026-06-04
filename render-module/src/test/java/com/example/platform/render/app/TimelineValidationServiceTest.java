package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.timeline.InternalTimelineValidationService;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TimelineValidationServiceTest {

    private final TimelineValidationService service =
            new TimelineValidationService(new InternalTimelineValidationService());

    @Test
    void rejectsEmptyJson() {
        assertFalse(service.validateJson("").valid());
    }

    @Test
    void validatesV1Sample() throws Exception {
        String json = Files.readString(FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json"));
        assertTrue(service.validateJson(json).valid());
    }

    @Test
    void rejectsLegacyTracksRoot() {
        String json = """
                {"id":"tl-1","tracks":[{"type":"VIDEO","clips":[]}],"outputSpec":{"format":"mp4"}}
                """;
        assertFalse(service.validateJson(json).valid());
    }
}
