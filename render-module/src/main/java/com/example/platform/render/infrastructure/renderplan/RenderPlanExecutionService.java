package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RenderPlan Service - orchestrates the render pipeline.
 * 
 * <p>Flow: Timeline → RenderPlan DAG → Execution Engine → Artifact
 */
@Service
public class RenderPlanExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RenderPlanExecutionService.class);

    private final RenderPlanBuilder planBuilder;
    private final DagExecutionEngine executionEngine;

    public RenderPlanExecutionService(RenderPlanBuilder planBuilder, DagExecutionEngine executionEngine) {
        this.planBuilder = planBuilder;
        this.executionEngine = executionEngine;
    }

    /**
     * Execute a render job.
     */
    public RenderPlanResult executeRender(String jobId, RenderPlanBuilder.TimelineData timeline) {
        log.info("Starting render for job {}", jobId);

        // Step 1: Build RenderPlan from Timeline
        RenderPlan plan = planBuilder.buildFromTimeline(jobId, timeline);
        log.info("Built render plan with {} nodes", plan.size());

        // Step 2: Execute DAG
        DagExecutionEngine.ExecutionResult result = executionEngine.execute(plan);
        log.info("Execution result: {}", result.getSummary());

        // Step 3: Return result
        return new RenderPlanResult(
                jobId,
                plan.planId(),
                result.success(),
                result.rootOutput(),
                result.durationMs(),
                result.errors()
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record RenderPlanResult(
            String jobId,
            String planId,
            boolean success,
            String outputUri,
            long durationMs,
            java.util.List<String> errors
    ) {}
}
