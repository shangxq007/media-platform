package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderPlannerServiceTest {

    private final RenderPlannerService planner = new RenderPlannerService(
            new TimelineExtensionsReader(), new FinalComposerSelector(), new TimelineStickerReader(),
            new com.example.platform.render.app.timeline.SegmentTimelinePlanner());

    @Test
    void simpleTimelineUsesFfmpegFinalComposer() {
        TimelineSpec spec = TimelineSpec.create("tl-1", "Simple", TimelineOutputSpec.mp4_1080p30());
        PipelineExecutionPlan plan = planner.generatePlan(spec, "default_1080p", "FREE", "mp4");
        assertEquals(FinalComposerHint.FFMPEG, plan.finalComposer());
        assertTrue(plan.tasks().stream().anyMatch(t -> t.type() == PipelineTaskType.FINAL_COMPOSE));
    }

    @Test
    void multitrackTimelineUsesMltFinalComposer() {
        TimelineTrack v1 = TimelineTrack.of("v1", "V1", TimelineTrack.TrackType.VIDEO);
        TimelineTrack v2 = TimelineTrack.of("v2", "V2", TimelineTrack.TrackType.VIDEO);
        TimelineSpec spec = new TimelineSpec("tl-m", "Multi", null,
                List.of(v1, v2), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        PipelineExecutionPlan plan = planner.generatePlan(spec, "default_1080p", "PRO", "mp4");
        assertEquals(FinalComposerHint.MLT, plan.finalComposer());
        assertTrue(plan.tasks().stream().anyMatch(t -> t.type() == PipelineTaskType.MLT_MULTITRACK));
    }

    @Test
    void dashOutputIncludesPackagingTask() {
        TimelineSpec spec = TimelineSpec.create("tl-d", "Dash", TimelineOutputSpec.mp4_1080p30());
        PipelineExecutionPlan plan = planner.generatePlan(spec, "default_1080p", "TEAM", "dash");
        assertTrue(plan.tasks().stream().anyMatch(t -> t.type() == PipelineTaskType.PACKAGING));
    }
}
