package com.example.platform.render.app.timeline;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.internal.IncrementalRenderPlan;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves incremental {@link PipelineExecutionPlan} for orchestrated DAG execution.
 */
@Service
public class IncrementalRenderOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalRenderOrchestrationService.class);

    private final IncrementalRenderPlanService incrementalRenderPlanService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final BaseJobTimelineLoader baseJobTimelineLoader;

    public IncrementalRenderOrchestrationService(
            IncrementalRenderPlanService incrementalRenderPlanService,
            TimelineSpecResolver timelineSpecResolver,
            BaseJobTimelineLoader baseJobTimelineLoader) {
        this.incrementalRenderPlanService = incrementalRenderPlanService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.baseJobTimelineLoader = baseJobTimelineLoader;
    }

    public Optional<IncrementalExecution> tryResolve(String newTimelineJson,
                                                     String baseJobId,
                                                     String tenantId,
                                                     TimelineSpec spec,
                                                     String profile,
                                                     String tier,
                                                     String outputFormat) {
        if (baseJobId == null || baseJobId.isBlank()) {
            return Optional.empty();
        }
        if (!timelineSpecResolver.isInternalTimelineJson(newTimelineJson)) {
            log.debug("Skipping incremental plan: new timeline is not Internal 1.0");
            return Optional.empty();
        }
        Optional<String> oldJson = baseJobTimelineLoader.loadInternalTimelineJson(baseJobId, tenantId);
        if (oldJson.isEmpty()) {
            log.info("Skipping incremental plan: base job {} has no Internal 1.0 timeline", baseJobId);
            return Optional.empty();
        }
        try {
            IncrementalRenderPlan plan = incrementalRenderPlanService.generate(
                    newTimelineJson,
                    oldJson.get(),
                    profile,
                    tier,
                    outputFormat,
                    baseJobId,
                    List.of(),
                    tenantId);
            log.info("Incremental render plan mode={} executeTasks={} reuseTasks={} baseJob={}",
                    plan.mode(), plan.executeTaskIds().size(), plan.reuseTaskIds().size(), baseJobId);
            return Optional.of(new IncrementalExecution(plan.pipelinePlan(), plan));
        } catch (Exception e) {
            log.warn("Incremental plan generation failed for baseJob={}, falling back to full DAG", baseJobId, e);
            return Optional.empty();
        }
    }

    public record IncrementalExecution(PipelineExecutionPlan plan, IncrementalRenderPlan incrementalPlan) {}
}
