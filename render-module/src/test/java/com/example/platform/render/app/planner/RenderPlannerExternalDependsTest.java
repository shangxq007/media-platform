package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RenderPlannerExternalDependsTest {

    @Test
    void externalNodesHonorDependsOnParam() {
        RenderPlannerService planner = new RenderPlannerService(
                new TimelineExtensionsReader(), new FinalComposerSelector(), new TimelineStickerReader(),
                new com.example.platform.render.app.timeline.SegmentTimelinePlanner());
        TimelineSpec spec = TimelineSpec.create("tl", "T", TimelineOutputSpec.mp4_1080p30());
        Map<String, String> meta = new java.util.LinkedHashMap<>(spec.metadata());
        meta.put("platform.externalRenderNodes", """
                [
                  {"id":"a","backend":"blender","timelineStart":0,"duration":2},
                  {"id":"b","backend":"remotion","timelineStart":2,"duration":2,
                   "params":{"dependsOn":["xr_a"]}}
                ]
                """);
        spec = new TimelineSpec(spec.id(), spec.name(), spec.description(), spec.tracks(),
                spec.textOverlays(), spec.outputSpec(), spec.totalDuration(), meta);

        PipelineExecutionPlan plan = planner.generatePlan(spec, "default_1080p", "PRO", "mp4");
        PipelineTask taskB = plan.tasks().stream()
                .filter(t -> "xr_b".equals(t.taskId()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("xr_a"), taskB.dependsOn());
    }
}
