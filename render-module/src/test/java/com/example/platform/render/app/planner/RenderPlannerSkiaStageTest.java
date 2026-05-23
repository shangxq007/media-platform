package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RenderPlannerSkiaStageTest {

    @Test
    void addsSkiaOverlayWhenStickersPresent() {
        RenderPlannerService planner = new RenderPlannerService(
                new TimelineExtensionsReader(), new FinalComposerSelector(), new TimelineStickerReader(),
                new com.example.platform.render.app.timeline.SegmentTimelinePlanner());
        ReflectionTestUtils.setField(planner, "skiaEnabled", true);

        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        Map<String, String> meta = new java.util.LinkedHashMap<>(spec.metadata());
        meta.put("platform.stickers", """
                [{"id":"s1","imageUri":"file:///tmp/sticker.png","x":10,"y":10,"width":64,"height":64,"startTime":0,"duration":2}]
                """);
        spec = new TimelineSpec(spec.id(), spec.name(), spec.description(), spec.tracks(),
                spec.textOverlays(), spec.outputSpec(), spec.totalDuration(), meta);

        PipelineExecutionPlan plan = planner.generatePlan(spec, "default_1080p", "PRO", "mp4");
        assertTrue(plan.tasks().stream().anyMatch(t -> t.type() == PipelineTaskType.SKIA_OVERLAY));
    }
}
