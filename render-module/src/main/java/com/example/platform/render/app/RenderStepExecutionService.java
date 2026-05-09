package com.example.platform.render.app;

import com.example.platform.render.domain.RenderPlan;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for executing individual {@link RenderStep} instances within a {@link RenderPlan}.
 *
 * <p>This service manages step lifecycle: transitioning from PENDING → RUNNING → COMPLETED/FAILED.
 * Actual tool execution is delegated to the appropriate provider (FFmpeg, MLT, GPAC)
 * through the {@link com.example.platform.extension.app.ProcessToolRunner} port.</p>
 */
@Service
public class RenderStepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RenderStepExecutionService.class);

    private final RenderPlanService planService;
    private final Map<String, RenderStep> activeSteps = new ConcurrentHashMap<>();

    public RenderStepExecutionService(RenderPlanService planService) {
        this.planService = planService;
    }

    /**
     * Executes the next pending step in the given plan.
     *
     * <p>This is a skeleton implementation that transitions the step through its
     * lifecycle. Actual tool invocation will be added when providers are wired.</p>
     *
     * @param planId the render plan ID
     * @return the updated plan
     * @throws IllegalStateException if no pending step exists or the plan is done
     */
    public RenderPlan executeNextStep(String planId) {
        RenderPlan plan = planService.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        if (plan.isDone()) {
            throw new IllegalStateException("Plan is already done: " + planId);
        }

        RenderStep step = plan.nextPendingStep();
        if (step == null) {
            throw new IllegalStateException("No pending step in plan: " + planId);
        }

        // Transition to RUNNING
        RenderStep running = step.markRunning();
        activeSteps.put(step.id(), running);
        plan = planService.save(plan.withStep(running));
        log.info("Step {} ({}) is now RUNNING for plan {}", step.id(), step.type(), planId);

        // Execute the step (skeleton — actual execution delegated to providers)
        RenderStep result = executeStep(running);
        activeSteps.remove(step.id());

        // Update plan with result
        plan = planService.save(plan.withStep(result));
        log.info("Step {} ({}) completed with status {} for plan {}",
                result.id(), result.type(), result.status(), planId);

        return plan;
    }

    /**
     * Executes a single step. This is the extension point for actual tool execution.
     *
     * <p>Subsequent prompts will wire FFmpeg, MLT, and GPAC providers here.</p>
     *
     * @param step the step to execute
     * @return the step with updated status
     */
    protected RenderStep executeStep(RenderStep step) {
        try {
            // Skeleton: simulate execution
            log.info("Executing step type: {}", step.type());
            return step.markCompleted(List.of(Ids.newId("art")));
        } catch (Exception e) {
            log.error("Step execution failed: {}", step.type(), e);
            return step.markFailed("EXECUTION_FAILED", e.getMessage());
        }
    }

    /**
     * Returns the currently executing step, if any.
     */
    public RenderStep getActiveStep(String stepId) {
        return activeSteps.get(stepId);
    }

    /**
     * Cancels a running step.
     *
     * @param planId the plan ID
     * @param stepId the step ID
     * @return the updated plan
     */
    public RenderPlan cancelStep(String planId, String stepId) {
        RenderStep active = activeSteps.get(stepId);
        if (active == null) {
            throw new IllegalArgumentException("Step is not currently running: " + stepId);
        }

        RenderStep cancelled = active.markCancelled();
        activeSteps.remove(stepId);

        RenderPlan plan = planService.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        return planService.save(plan.withStep(cancelled));
    }
}
