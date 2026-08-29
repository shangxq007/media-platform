package com.example.platform.render.app.planner;

import com.example.platform.render.domain.RenderJobPlan;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepType;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.shared.Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Bridges {@link PipelineExecutionPlan} to domain {@link RenderJobPlan} steps for job orchestration.
 */
@Service
public class RenderPlanBridgeService {

    public RenderJobPlan toDomainRenderPlan(String renderJobId, RenderProfile profile,
                                         TimelineSpec timeline, PipelineExecutionPlan pipeline) {
        String planId = Ids.newId("rp");
        List<RenderStep> steps = new ArrayList<>();
        steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.BUILD_TIMELINE));

        for (PipelineTask task : pipeline.tasks()) {
            RenderStepType stepType = mapTaskType(task);
            if (stepType != null) {
                steps.add(RenderStep.pending(Ids.newId("rs"), planId, stepType));
            }
        }

        if (steps.stream().noneMatch(s -> s.type() == RenderStepType.REGISTER_ARTIFACT)) {
            steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.REGISTER_ARTIFACT));
        }

        Map<String, String> params = Map.of(
                "pipelinePlanId", pipeline.planId(),
                "timelineId", timeline.id(),
                "finalComposer", pipeline.finalComposer().name().toLowerCase());
        return RenderJobPlan.create(planId, renderJobId, profile, steps, params);
    }

    private RenderStepType mapTaskType(PipelineTask task) {
        return switch (task.type()) {
            case EXTERNAL_RENDER -> switch (task.backend()) {
                case "remotion" -> null; // future REMOTION_RENDER step
                case "natron" -> null;
                default -> RenderStepType.PROVIDER_TRANSCODE;
            };
            case MLT_MULTITRACK, FINAL_COMPOSE -> RenderStepType.MLT_RENDER_TIMELINE;
            case SEGMENT_RENDER, EFFECTS, TRANSCODE, ENCODE -> RenderStepType.PROVIDER_TRANSCODE;
            case PACKAGING -> task.parameters() != null
                    && "hls".equalsIgnoreCase(task.parameters().getOrDefault("format", "dash"))
                    ? RenderStepType.GPAC_PACKAGE_HLS
                    : RenderStepType.GPAC_PACKAGE_DASH;
            case QA -> RenderStepType.QC_PROBE;
            case SUBTITLES, SKIA_OVERLAY -> RenderStepType.PROVIDER_TRANSCODE;
        };
    }
}
