package com.example.platform.render.app;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimelineExecutorServiceTest {

    private final TimelineExecutorService executor = new TimelineExecutorService(
            new RenderPlannerService(new TimelineExtensionsReader(), new FinalComposerSelector(),
                    new com.example.platform.render.domain.timeline.TimelineStickerReader(),
                    new com.example.platform.render.app.timeline.SegmentTimelinePlanner()));

    @Test
    void multitrackTimelineAddsMltStage() {
        TimelineTrack v1 = TimelineTrack.of("v1", "V1", TimelineTrack.TrackType.VIDEO);
        TimelineTrack v2 = TimelineTrack.of("v2", "V2", TimelineTrack.TrackType.VIDEO);
        TimelineSpec spec = new TimelineSpec("tl-m", "Multi", null,
                List.of(v1, v2), List.of(), TimelineOutputSpec.mp4_1080p30(), 10, Map.of());

        var plan = executor.plan(spec, "default_1080p", "PRO", "mp4");
        assertTrue(plan.stages().stream().anyMatch(s -> "mlt_multitrack".equals(s.name())));
    }

    @Test
    void textOverlaysAddLibassStage() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("t1", "Hello", 0, 5);
        TimelineSpec spec = new TimelineSpec("tl-l", "Libass", null,
                List.of(TimelineTrack.of("v1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), TimelineOutputSpec.mp4_1080p30(), 5, Map.of());

        var plan = executor.plan(spec, "default_1080p", "FREE", "mp4");
        assertTrue(plan.stages().stream().anyMatch(s -> "subtitles".equals(s.name())
                && "libass".equals(s.providerKey())));
    }

    @Test
    void dashDrmSelectsBento4Packager() {
        TimelineSpec spec = TimelineSpec.create("tl-d", "Dash", TimelineOutputSpec.mp4_1080p30());
        var plan = executor.plan(spec, "default_1080p", "TEAM", "dash_drm");
        assertTrue(plan.stages().stream().anyMatch(s -> "packaging".equals(s.name())
                && "bento4".equals(s.providerKey())));
    }
}
