package com.example.platform.render.app;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.RenderPlannerService;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * L2 timeline executor: explicit multi-stage plan (MLT multitrack, OFX effects, libass, transcode, packaging).
 */
@Service
public class TimelineExecutorService {

    private final RenderPlannerService renderPlannerService;

    public TimelineExecutorService(RenderPlannerService renderPlannerService) {
        this.renderPlannerService = renderPlannerService;
    }

    public TimelineExecutionPlan plan(TimelineSpec timeline, String profile, String tier, String outputFormat) {
        PipelineExecutionPlan pipeline = renderPlannerService.generatePlan(timeline, profile, tier, outputFormat);
        List<MultiProviderPipelineService.PipelineStage> stages =
                renderPlannerService.toPipelineStages(pipeline);
        Map<String, String> meta = new LinkedHashMap<>(pipeline.metadata());
        meta.put("pipelinePlanId", pipeline.planId());
        return new TimelineExecutionPlan(timeline.id(), stages, meta);
    }

    public PipelineExecutionPlan planPipeline(TimelineSpec timeline, String profile, String tier,
                                              String outputFormat) {
        return renderPlannerService.generatePlan(timeline, profile, tier, outputFormat);
    }

    /**
     * Stages for {@link MultiProviderPipelineService}, including incremental skip/reuse parameters.
     */
    public List<MultiProviderPipelineService.PipelineStage> stagesFromPlan(PipelineExecutionPlan plan) {
        return renderPlannerService.toPipelineStages(plan);
    }

    public record TimelineExecutionPlan(
            String timelineId,
            List<MultiProviderPipelineService.PipelineStage> stages,
            Map<String, String> metadata) {}
}
