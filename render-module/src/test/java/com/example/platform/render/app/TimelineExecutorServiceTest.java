package com.example.platform.render.app;

import com.example.platform.render.app.planner.FinalComposerSelector;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.planner.RenderPlannerService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineStickerReader;
import com.example.platform.render.domain.legacy.TimelineTrack;

class TimelineExecutorServiceTest {

    private final TimelineExecutorService executor = new TimelineExecutorService(
            new RenderPlannerService(new TimelineExtensionsReader(), new FinalComposerSelector(),
                    new com.example.platform.render.domain.legacy.TimelineStickerReader(),
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
    void textOverlaysAddProviderNeutralSubtitleTask() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("t1", "Hello",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 0, 5);
        TimelineSpec spec = new TimelineSpec("tl-l", "Subtitles", null,
                List.of(TimelineTrack.of("v1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), TimelineOutputSpec.mp4_1080p30(), 5, Map.of());

        var plan = executor.planPipeline(spec, "default_1080p", "FREE", "mp4");
        var subtitlesTask = plan.tasks().stream()
                .filter(task -> task.type() == PipelineTaskType.SUBTITLES)
                .findFirst()
                .orElseThrow();
        assertNull(subtitlesTask.backend());
        assertEquals("subtitle.burn-in", subtitlesTask.parameters().get("capability"));
    }

    @Test
    void dashDrmSelectsBento4Packager() {
        TimelineSpec spec = TimelineSpec.create("tl-d", "Dash", TimelineOutputSpec.mp4_1080p30());
        var plan = executor.plan(spec, "default_1080p", "TEAM", "dash_drm");
        assertTrue(plan.stages().stream().anyMatch(s -> "packaging".equals(s.name())
                && "bento4".equals(s.providerKey())));
    }
}
