package com.example.platform.render.app.planner;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.FinalComposerHint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineDagTopologyTest {

    @Test
    void ordersTasksByDependencies() {
        PipelineTask external = PipelineTask.of("xr1", "ext", PipelineTaskType.EXTERNAL_RENDER,
                "blender", List.of(), Map.of());
        PipelineTask compose = PipelineTask.of("fc", "compose", PipelineTaskType.FINAL_COMPOSE,
                "mlt", List.of("xr1"), Map.of());
        PipelineTask transcode = PipelineTask.of("tc", "transcode", PipelineTaskType.TRANSCODE,
                "javacv", List.of("fc"), Map.of());

        PipelineExecutionPlan plan = new PipelineExecutionPlan(
                "p1", "tl", FinalComposerHint.MLT, List.of(transcode, compose, external), Map.of());

        List<List<PipelineTask>> waves = PipelineDagTopology.executionWaves(plan);
        assertEquals(3, waves.size());
        assertEquals("xr1", waves.get(0).get(0).taskId());
        assertEquals("fc", waves.get(1).get(0).taskId());
        assertEquals("tc", waves.get(2).get(0).taskId());
    }
}
