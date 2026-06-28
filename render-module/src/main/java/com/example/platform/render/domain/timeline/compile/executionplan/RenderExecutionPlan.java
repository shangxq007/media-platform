package com.example.platform.render.domain.timeline.compile.executionplan;

import com.example.platform.render.domain.timeline.compile.binding.ProviderBindingPlan;
import com.example.platform.render.domain.timeline.compile.execution.ProviderExecutionDocumentDraft;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Render execution plan — the deterministic planning structure that
 * maps a ProviderBindingPlan into executable steps.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All steps are planning placeholders with executionReady=false.
 * The plan does not execute providers, does not generate commands,
 * and does not mutate StorageRuntime or ProductRuntime.</p>
 *
 * @param planId          deterministic plan identifier
 * @param bindingPlanId   source ProviderBindingPlan ID
 * @param timelineId      source timeline identifier
 * @param policy          execution policy used for this plan
 * @param environmentTarget execution environment target
 * @param steps           ordered execution steps
 * @param executionReady  whether all steps are execution-ready (false in v0)
 * @param failureReasons  plan-level failure reasons (empty if valid)
 */
public record RenderExecutionPlan(
        RenderExecutionPlanId planId,
        String bindingPlanId,
        String timelineId,
        ExecutionPolicy policy,
        ExecutionEnvironmentTarget environmentTarget,
        List<RenderExecutionStep> steps,
        boolean executionReady,
        List<RenderExecutionPlanFailureReason> failureReasons) {

    /**
     * Returns all provider execution steps.
     */
    public List<RenderExecutionStep> providerExecutionSteps() {
        return steps.stream()
                .filter(RenderExecutionStep::isProviderExecution)
                .toList();
    }

    /**
     * Returns all materialization steps.
     */
    public List<RenderExecutionStep> materializationSteps() {
        return steps.stream()
                .filter(s -> s.type() == RenderExecutionStepType.MATERIALIZE_INPUT)
                .toList();
    }

    /**
     * Returns all finalization steps.
     */
    public List<RenderExecutionStep> finalizationSteps() {
        return steps.stream()
                .filter(s -> s.type() == RenderExecutionStepType.FINALIZE_RENDER)
                .toList();
    }

    /**
     * Returns steps that reference the final render output node.
     */
    public List<RenderExecutionStep> finalOutputSteps() {
        return steps.stream()
                .filter(RenderExecutionStep::isFinalOutput)
                .toList();
    }

    /**
     * Returns all failed steps.
     */
    public List<RenderExecutionStep> failedSteps() {
        return steps.stream()
                .filter(RenderExecutionStep::isFailed)
                .toList();
    }

    /**
     * Returns all blocked steps.
     */
    public List<RenderExecutionStep> blockedSteps() {
        return steps.stream()
                .filter(RenderExecutionStep::isBlocked)
                .toList();
    }

    /**
     * Returns true if the plan has failure reasons.
     */
    public boolean hasFailures() {
        return failureReasons != null && !failureReasons.isEmpty();
    }

    /**
     * Returns a summary of this plan for logging/diagnostics.
     */
    public RenderExecutionPlanSummary summary() {
        List<String> providers = steps.stream()
                .filter(s -> s.providerName() != null)
                .map(RenderExecutionStep::providerName)
                .distinct()
                .sorted()
                .toList();

        return new RenderExecutionPlanSummary(
                planId.toString(),
                bindingPlanId,
                timelineId,
                policy.mode(),
                environmentTarget,
                steps.size(),
                (int) steps.stream().filter(s -> s.status() == RenderExecutionStepStatus.PENDING).count(),
                (int) steps.stream().filter(RenderExecutionStep::isFailed).count(),
                (int) steps.stream().filter(RenderExecutionStep::isBlocked).count(),
                executionReady,
                providers,
                failureReasons != null ? failureReasons : List.of());
    }

    /**
     * Returns the step with the given ID, or null if not found.
     */
    public RenderExecutionStep findStep(String stepId) {
        return steps.stream()
                .filter(s -> s.stepId().equals(stepId))
                .findFirst()
                .orElse(null);
    }
}
