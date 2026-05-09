package com.example.platform.render.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single step within a {@link RenderPlan}.
 *
 * <p>Each step has a type, status, input/output artifacts, and execution metadata.
 * Steps are executed in order, and their status transitions are validated.</p>
 *
 * @param id            unique step identifier
 * @param planId        the render plan this step belongs to
 * @param type          the type of operation to perform
 * @param status        current execution status
 * @param inputArtifactIds  IDs of input artifacts consumed by this step
 * @param outputArtifactIds IDs of output artifacts produced by this step
 * @param errorCode     error code if the step failed
 * @param errorMessage  error message if the step failed
 * @param startedAt     when the step started executing
 * @param completedAt   when the step completed (success or failure)
 * @param duration      execution duration
 * @param parameters    step-specific parameters
 */
public record RenderStep(
        String id,
        String planId,
        RenderStepType type,
        RenderStepStatus status,
        List<String> inputArtifactIds,
        List<String> outputArtifactIds,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        Map<String, String> parameters) {

    /**
     * Creates a new pending step.
     */
    public static RenderStep pending(String id, String planId, RenderStepType type) {
        return new RenderStep(id, planId, type, RenderStepStatus.PENDING,
                List.of(), List.of(), null, null, null, null, null, Map.of());
    }

    /**
     * Creates a new pending step with parameters.
     */
    public static RenderStep pending(String id, String planId, RenderStepType type,
            Map<String, String> parameters) {
        return new RenderStep(id, planId, type, RenderStepStatus.PENDING,
                List.of(), List.of(), null, null, null, null, null, parameters);
    }

    /**
     * Returns a copy with status set to RUNNING.
     */
    public RenderStep markRunning() {
        return new RenderStep(id, planId, type, RenderStepStatus.RUNNING,
                inputArtifactIds, outputArtifactIds, null, null, Instant.now(), null, null, parameters);
    }

    /**
     * Returns a copy with status set to COMPLETED.
     */
    public RenderStep markCompleted(List<String> outputIds) {
        Instant now = Instant.now();
        Duration dur = startedAt != null ? Duration.between(startedAt, now) : null;
        return new RenderStep(id, planId, type, RenderStepStatus.COMPLETED,
                inputArtifactIds, outputIds, null, null, startedAt, now, dur, parameters);
    }

    /**
     * Returns a copy with status set to FAILED.
     */
    public RenderStep markFailed(String errorCode, String errorMessage) {
        Instant now = Instant.now();
        Duration dur = startedAt != null ? Duration.between(startedAt, now) : null;
        return new RenderStep(id, planId, type, RenderStepStatus.FAILED,
                inputArtifactIds, outputArtifactIds, errorCode, errorMessage, startedAt, now, dur, parameters);
    }

    /**
     * Returns a copy with status set to CANCELLED.
     */
    public RenderStep markCancelled() {
        Instant now = Instant.now();
        Duration dur = startedAt != null ? Duration.between(startedAt, now) : null;
        return new RenderStep(id, planId, type, RenderStepStatus.CANCELLED,
                inputArtifactIds, outputArtifactIds, null, "Cancelled", startedAt, now, dur, parameters);
    }

    /**
     * Validates a status transition.
     *
     * @throws IllegalArgumentException if the transition is not valid
     */
    public RenderStep withStatus(RenderStepStatus newStatus) {
        validateTransition(this.status, newStatus);
        return new RenderStep(id, planId, type, newStatus,
                inputArtifactIds, outputArtifactIds, errorCode, errorMessage,
                startedAt, completedAt, duration, parameters);
    }

    static void validateTransition(RenderStepStatus from, RenderStepStatus to) {
        if (from == to) {
            return;
        }
        boolean valid = switch (from) {
            case PENDING -> to == RenderStepStatus.RUNNING
                    || to == RenderStepStatus.SKIPPED
                    || to == RenderStepStatus.CANCELLED;
            case RUNNING -> to == RenderStepStatus.COMPLETED
                    || to == RenderStepStatus.FAILED
                    || to == RenderStepStatus.CANCELLED;
            case FAILED -> to == RenderStepStatus.PENDING; // retry
            case COMPLETED, CANCELLED, SKIPPED -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    String.format("Invalid step status transition from %s to %s", from, to));
        }
    }
}
