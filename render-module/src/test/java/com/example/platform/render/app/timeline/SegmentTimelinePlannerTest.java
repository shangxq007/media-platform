package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SegmentTimelinePlannerTest {

    private final SegmentTimelinePlanner planner = new SegmentTimelinePlanner();

    @Test
    void plansSegmentsWhenPolicyEnabled() throws Exception {
        String json = Files.readString(samplePath());
        var plan = planner.planFromTimelineJson(json);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().segments().size() >= 2);
        assertTrue(plan.get().segments().get(0).id().startsWith("seg_"));
    }

    @Test
    void packagingOnlyChangeLeavesNoDirtySegments() throws Exception {
        String base = Files.readString(samplePath());
        String patched = base.replace("\"segmentDurationSec\": 4", "\"segmentDurationSec\": 6");
        var plan = planner.planFromTimelineJson(patched).orElseThrow();
        var diff = new TimelineSemanticDiffService(new TimelineCanonicalizer())
                .diff(base, patched);
        var dirty = planner.dirtySegmentTaskIds(diff, patched, plan);
        assertTrue(dirty.isEmpty());
    }

    private static Path samplePath() {
        Path path = Path.of("../../docs/media-rendering/examples/timeline-v1-full-sample.json");
        if (!Files.exists(path)) {
            path = Path.of("docs/media-rendering/examples/timeline-v1-full-sample.json");
        }
        return path;
    }
}
