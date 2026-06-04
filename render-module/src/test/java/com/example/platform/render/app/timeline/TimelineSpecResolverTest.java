package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TimelineSpecResolverTest {

    private final TimelineSpecResolver resolver = new TimelineSpecResolver(
            TimelineTestSupport.internalTimelineAdapter(),
            new TimelineScriptParser());

    @Test
    void resolvesInternalTimelineV1Sample() throws Exception {
        Path sample = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json")
                .normalize().toAbsolutePath();
        String json = Files.readString(sample);
        var spec = resolver.resolve(json);
        assertTrue(spec.isPresent());
        assertTrue(resolver.isInternalTimelineJson(json));
        assertNotNull(spec.get().tracks());
        assertFalse(spec.get().tracks().isEmpty());
    }

    @Test
    void fallsBackToLegacyOtioTracks() {
        TimelineSpec created = TimelineSpec.create("tl-legacy", "Legacy", TimelineOutputSpec.mp4_1080p30());
        String otio = "{\"tracks\":[{\"name\":\"Video 1\",\"children\":[]}]}";
        assertTrue(resolver.resolve(otio).isPresent());
        assertFalse(resolver.isInternalTimelineJson(otio));
    }
}
