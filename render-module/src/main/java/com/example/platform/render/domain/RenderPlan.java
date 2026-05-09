package com.example.platform.render.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A render plan describes the sequence of steps required to produce a render job's output.
 */
public record RenderPlan(
        String id,
        String renderJobId,
        RenderProfile profile,
        List<RenderStep> steps,
        RenderStepStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, String> parameters) {

    /**
     * Creates a new render plan with the given profile and steps.
     */
    public static RenderPlan create(String id, String renderJobId, RenderProfile profile,
            List<RenderStep> steps) {
        List<RenderStep> stepList = new ArrayList<>(steps);
        RenderStepStatus derivedStatus = deriveStatus(stepList);
        return new RenderPlan(id, renderJobId, profile, stepList,
                derivedStatus, Instant.now(), null, null, Map.of());
    }

    /**
     * Creates a new render plan with parameters.
     */
    public static RenderPlan create(String id, String renderJobId, RenderProfile profile,
            List<RenderStep> steps, Map<String, String> parameters) {
        List<RenderStep> stepList = new ArrayList<>(steps);
        RenderStepStatus derivedStatus = deriveStatus(stepList);
        return new RenderPlan(id, renderJobId, profile, stepList,
                derivedStatus, Instant.now(), null, null, parameters);
    }

    /**
     * Returns the next pending step, or {@code null} if all steps are done.
     */
    public RenderStep nextPendingStep() {
        return steps.stream()
                .filter(s -> s.status() == RenderStepStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns {@code true} if all steps are completed.
     */
    public boolean isComplete() {
        return steps.stream().allMatch(s -> s.status() == RenderStepStatus.COMPLETED);
    }

    /**
     * Returns {@code true} if any step has failed.
     */
    public boolean hasFailed() {
        return steps.stream().anyMatch(s -> s.status() == RenderStepStatus.FAILED);
    }

    /**
     * Returns {@code true} if the plan is fully done (all completed or any failed/cancelled).
     */
    public boolean isDone() {
        return isComplete() || hasFailed() || status == RenderStepStatus.CANCELLED;
    }

    /**
     * Returns a copy with the given step updated.
     */
    public RenderPlan withStep(RenderStep updatedStep) {
        List<RenderStep> updatedSteps = steps.stream()
                .map(s -> s.id().equals(updatedStep.id()) ? updatedStep : s)
                .toList();
        RenderStepStatus newStatus = deriveStatus(updatedSteps);
        return new RenderPlan(id, renderJobId, profile, updatedSteps, newStatus,
                createdAt, startedAt, completedAt, parameters);
    }

    static RenderStepStatus deriveStatus(List<RenderStep> stepList) {
        boolean anyFailed = stepList.stream().anyMatch(s -> s.status() == RenderStepStatus.FAILED);
        boolean anyRunning = stepList.stream().anyMatch(s -> s.status() == RenderStepStatus.RUNNING);
        boolean allCompleted = stepList.stream().allMatch(s -> s.status() == RenderStepStatus.COMPLETED);
        boolean anyCancelled = stepList.stream().anyMatch(s -> s.status() == RenderStepStatus.CANCELLED);

        if (anyFailed) return RenderStepStatus.FAILED;
        if (anyCancelled) return RenderStepStatus.CANCELLED;
        if (allCompleted) return RenderStepStatus.COMPLETED;
        if (anyRunning) return RenderStepStatus.RUNNING;
        return RenderStepStatus.PENDING;
    }
}
