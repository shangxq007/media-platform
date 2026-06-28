package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.executionplan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a RenderExecutionPlan locally for FFmpeg baseline only.
 *
 * <p>Internal only — orchestrates step execution in dependency order.
 * Only FFmpeg PRODUCTION LOCAL steps are executable.</p>
 *
 * <p>v0: Executes FFmpeg baseline steps. Non-FFmpeg steps are skipped.</p>
 *
 * <p>This service does NOT expose provider internals, raw commands,
 * storage paths, or environment details in public APIs.</p>
 */
@Service
public class LocalExecutionPlanRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalExecutionPlanRunner.class);

    private final RenderPlanPolicyGuard policyGuard;
    private final RenderExecutionStepExecutor stepExecutor;

    public LocalExecutionPlanRunner(RenderPlanPolicyGuard policyGuard,
                                     RenderExecutionStepExecutor stepExecutor) {
        this.policyGuard = policyGuard;
        this.stepExecutor = stepExecutor;
    }

    /**
     * Run a render execution plan.
     *
     * @param plan    the execution plan to run
     * @param context the execution context (input product IDs, tenant, project, etc.)
     * @return the run result
     */
    public LocalExecutionPlanRunResult run(RenderExecutionPlan plan,
                                            LocalExecutionPlanContext context) {
        if (plan == null) {
            return LocalExecutionPlanRunResult.failedClosed("Plan must not be null");
        }
        if (context == null) {
            return LocalExecutionPlanRunResult.failedClosed("Context must not be null");
        }

        // Step 1: Policy guard check
        RenderPlanPolicyResult policyResult = policyGuard.evaluate(plan, plan.policy());
        if (policyResult.isRejected()) {
            log.warn("Plan rejected by policy guard: {}", policyResult.explanation());
            return LocalExecutionPlanRunResult.failedClosed(
                    "Policy guard rejected plan: " + policyResult.explanation());
        }

        // Step 2: Check execution readiness — only FFmpeg LOCAL PRODUCTION steps are allowed
        if (!isExecutionAllowed(plan)) {
            return LocalExecutionPlanRunResult.notExecutable(
                    "Plan contains non-executable steps (only FFmpeg LOCAL PRODUCTION is allowed)");
        }

        // Step 3: Generate local execution run ID
        String localExecutionRunId = "ler-" + java.util.UUID.randomUUID().toString().substring(0, 12);

        // Step 4: Execute steps in dependency order
        log.info("Starting plan execution: planId={} localRunId={} steps={} context={}",
                plan.planId(), localExecutionRunId, plan.steps().size(), context.summary());

        List<LocalExecutionPlanStepResult> stepResults = new ArrayList<>();
        boolean allSucceeded = true;

        for (RenderExecutionStep step : plan.steps()) {
            if (isStepBlockedByFailedDependency(step, stepResults)) {
                LocalExecutionPlanStepResult blockedResult = LocalExecutionPlanStepResult.blocked(
                        step.stepId(), step.type().name(),
                        "Blocked by failed upstream dependency");
                stepResults.add(blockedResult);
                allSucceeded = false;
                continue;
            }

            LocalExecutionPlanStepResult stepResult = stepExecutor.execute(step, context);
            stepResults.add(stepResult);

            if (stepResult.status() == LocalExecutionPlanRunStatus.FAILED
                    || stepResult.status() == LocalExecutionPlanRunStatus.FAILED_CLOSED) {
                allSucceeded = false;
                log.warn("Step failed: stepId={} status={} message={}",
                        step.stepId(), stepResult.status(), stepResult.message());
            }
        }

        LocalExecutionPlanRunStatus overallStatus = allSucceeded
                ? LocalExecutionPlanRunStatus.SUCCEEDED
                : LocalExecutionPlanRunStatus.FAILED;

        log.info("Plan execution completed: planId={} status={} steps={}",
                plan.planId(), overallStatus, stepResults.size());

        return new LocalExecutionPlanRunResult(
                overallStatus, plan.planId().toString(), localExecutionRunId, stepResults,
                allSucceeded ? "All steps succeeded" : "One or more steps failed",
                context.outputProductId() != null ? context.outputProductId() : null);
    }

    /**
     * Check if the plan is allowed to execute (only FFmpeg LOCAL PRODUCTION).
     */
    private boolean isExecutionAllowed(RenderExecutionPlan plan) {
        // v0: only allow plans that target LOCAL environment
        if (plan.environmentTarget() != ExecutionEnvironmentTarget.LOCAL) {
            return false;
        }
        // Check that all EXECUTE_PROVIDER steps are FFmpeg
        for (RenderExecutionStep step : plan.providerExecutionSteps()) {
            if (step.providerName() == null || !"ffmpeg".equals(step.providerName())) {
                log.warn("Non-FFmpeg provider step not executable: {} provider={}",
                        step.stepId(), step.providerName());
                return false;
            }
            if (step.providerRef() != null
                    && step.providerRef().providerStatus()
                    != com.example.platform.render.infrastructure.ProviderStatus.PRODUCTION) {
                log.warn("Non-production provider step not executable: {} status={}",
                        step.stepId(), step.providerRef().providerStatus());
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a step is blocked by a failed dependency.
     */
    private boolean isStepBlockedByFailedDependency(RenderExecutionStep step,
                                                     List<LocalExecutionPlanStepResult> completedSteps) {
        if (step.dependencies() == null || step.dependencies().isEmpty()) {
            return false;
        }
        for (String depId : step.dependencies()) {
            for (LocalExecutionPlanStepResult completed : completedSteps) {
                if (completed.stepId().equals(depId)
                        && (completed.status() == LocalExecutionPlanRunStatus.FAILED
                            || completed.status() == LocalExecutionPlanRunStatus.FAILED_CLOSED)) {
                    return true;
                }
            }
        }
        return false;
    }
}
