package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TimelineRevisionDiffServiceTest {

    private final TimelineRevisionDiffService diffService = new TimelineRevisionDiffService();

    @Test
    void summarizesClipAndTrackChanges() throws Exception {
        Path sample = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json")
                .normalize()
                .toAbsolutePath();
        String base = Files.readString(sample);
        String modified = base.replace("\"revision\": 42", "\"revision\": 43");
        TimelineRevisionDiffService.ChangeSummary summary = diffService.summarize(base, modified);
        assertTrue(summary.supported());
        assertEquals(42, summary.parentInternalRevision());
        assertEquals(43, summary.currentInternalRevision());
    }
}
