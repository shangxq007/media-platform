package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.timeline.InternalTimelineValidationService;
import com.example.platform.render.app.timeline.TimelineCanonicalizer;
import com.example.platform.render.app.timeline.TimelineTestSupport;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.fasterxml.jackson.databind.node.TextNode;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelinePatchServiceTest {

    private final TimelinePatchService patchService = new TimelinePatchService(
            new TimelineValidationService(new InternalTimelineValidationService()),
            TimelineTestSupport.internalTimelineAdapter(),
            new TimelineCanonicalizer());

    @Test
    void replaceSubtitleCueText() throws Exception {
        String base = loadSample();
        var result = patchService.applyPatch(base, List.of(
                new TimelinePatchService.PatchOperation(
                        "replace",
                        "/composition/subtitleTracks/0/cues/0/text",
                        new TextNode("新标题"))));
        assertTrue(result.success());
        assertTrue(result.timelineJson().contains("新标题"));
        assertTrue(result.timelineJson().contains("\"revision\" : 43")
                || result.timelineJson().contains("\"revision\": 43"));
    }

    @Test
    void rejectsLegacyTracksJson() {
        String legacy = """
                {"id":"tl-legacy","tracks":[{"id":"t1","type":"VIDEO","clips":[]}]}
                """;
        var result = patchService.applyPatch(legacy, List.of(
                new TimelinePatchService.PatchOperation(
                        "replace", "/name", new TextNode("x"))));
        assertFalse(result.success());
    }

    private String loadSample() throws Exception {
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        return Files.readString(path);
    }
}
