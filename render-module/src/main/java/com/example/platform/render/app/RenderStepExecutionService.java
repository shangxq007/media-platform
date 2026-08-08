package com.example.platform.render.app;

import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.render.domain.RenderPlan;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for executing individual {@link RenderStep} instances within a {@link RenderPlan}.
 *
 * <p>This service manages step lifecycle: transitioning from PENDING → RUNNING → COMPLETED/FAILED.
 * Actual tool execution is delegated to the appropriate provider (FFmpeg, MLT, GPAC)
 * through the {@link com.example.platform.extension.app.ProcessToolRunner} port.</p>
 *
 * <p>On step completion it emits a canonical DURATION usage record as an additive side effect.
 * Emission is independent of billing enforcement: the {@code billing.enforcement.enabled} flag
 * does NOT suppress usage facts. Emission never alters step lifecycle semantics and never throws
 * into the execution path.</p>
 */
@Service
public class RenderStepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RenderStepExecutionService.class);

    private final RenderPlanService planService;
    private final UsageRecordEmissionPort emissionPort;
    private final Map<String, RenderStep> activeSteps = new ConcurrentHashMap<>();

    @Autowired
    public RenderStepExecutionService(RenderPlanService planService,
            UsageRecordEmissionPort emissionPort) {
        this.planService = planService;
        this.emissionPort = emissionPort;
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

        // Additive usage side effect at the step-completion boundary (operation fact).
        emitStepUsage(plan, result, 1);

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

    /**
     * Emits one canonical DURATION {@link UsageRecord} for a completed step.
     *
     * <p>The idempotency key is {@code "render-" + stepId + "-" + attempt}: derived from the
     * step identity plus attempt, so it is stable across retries of the same attempt (a retry of
     * the same attempt reuses the key and does not double count) and a new attempt produces a new
     * key. This method is the single emission boundary for render; it is intentionally NOT wired
     * to {@code billing.enforcement.enabled} — suppressing enforcement must never drop usage
     * facts (RED-004). Any emission failure is swallowed so it can never break step execution.</p>
     *
     * @param plan      the plan the step belongs to
     * @param step      the completed step (its {@link RenderStep#duration() duration} is the fact)
     * @param attempt   the attempt number for this step execution
     */
    void emitStepUsage(RenderPlan plan, RenderStep step, int attempt) {
        if (emissionPort == null) {
            return;
        }
        if (step == null || step.status() != RenderStepStatus.COMPLETED) {
            return;
        }
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            log.warn("RenderStepExecutionService: skipping usage emission, no tenant context for step {} (tenant required)",
                    step.id());
            return;
        }
        Duration duration = step.duration();
        if (duration == null) {
            log.warn("RenderStepExecutionService: skipping usage emission, no duration fact for step {}",
                    step.id());
            return;
        }
        try {
            String stepId = step.id();
            UsageRecord record = UsageRecord.record(
                    tenantId,
                    null,
                    null,
                    OperationRef.of(plan.id(), stepId),
                    null,
                    null,
                    step.type() != null ? step.type().name() : null,
                    UsageDimension.DURATION,
                    UsageQuantity.fromBaseUnits(duration.toMillis(), UsageUnit.MILLISECONDS),
                    step.completedAt(),
                    Instant.now(),
                    Instant.now(),
                    "render-" + stepId + "-" + attempt,
                    "REPORTED",
                    "render-step");
            emissionPort.emit(record);
        } catch (Exception e) {
            log.warn("RenderStepExecutionService: usage emission failed for step {}: {}",
                    step.id(), e.getMessage());
        }
    }
}
