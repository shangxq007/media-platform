package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.domain.timeline.FinalComposerHint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SegmentPlanFilterTest {

    private final SegmentPlanFilter filter = new SegmentPlanFilter();

    @Test
    void restrictsNonTargetSegmentsToReuse() {
        PipelineExecutionPlan plan = new PipelineExecutionPlan(
                "p1",
                "tl",
                FinalComposerHint.FFMPEG,
                List.of(
                        PipelineTask.of("seg_0", "seg_0", PipelineTaskType.SEGMENT_RENDER, "ffmpeg",
                                List.of(), Map.of()),
                        PipelineTask.of("seg_1", "seg_1", PipelineTaskType.SEGMENT_RENDER, "ffmpeg",
                                List.of(), Map.of()),
                        PipelineTask.of("final_compose", "final_compose", PipelineTaskType.FINAL_COMPOSE, "ffmpeg",
                                List.of("seg_0", "seg_1"), Map.of())),
                Map.of());

        PipelineExecutionPlan filtered = filter.restrictToTargetSegments(
                plan, Set.of("seg_1"), Map.of("seg_0", "localFs://base/seg_0.mp4"));

        PipelineTask seg0 = filtered.tasks().stream().filter(t -> "seg_0".equals(t.taskId())).findFirst().orElseThrow();
        PipelineTask seg1 = filtered.tasks().stream().filter(t -> "seg_1".equals(t.taskId())).findFirst().orElseThrow();
        assertEquals("reuse", seg0.parameters().get("incrementalMode"));
        assertEquals("true", seg0.parameters().get("skipExecution"));
        assertEquals("localFs://base/seg_0.mp4", seg0.parameters().get("reuseArtifactUri"));
        assertNull(seg1.parameters().get("skipExecution"));
        assertEquals("PARTIAL", filtered.metadata().get("segmentFilterMode"));
    }
}
