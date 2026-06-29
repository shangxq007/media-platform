package com.example.platform.render.domain.timeline.editing;

import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of applying a timeline edit request.
 * Immutable record. Internal domain model.
 *
 * <p>Side-effect-free: does not persist, render, create Product, or call StorageRuntime/ProductRuntime.</p>
 *
 * @param status    result status
 * @param timeline  updated timeline (if applied) or original timeline (if failed/no-op)
 * @param issues    validation/operation issues
 * @param safeMetadata safe metadata only
 */
public record TimelineEditResult(
        TimelineEditResultStatus status,
        TimelineSpec timeline,
        List<TimelineValidationIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineEditResult {
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static TimelineEditResult applied(TimelineSpec timeline) {
        return new TimelineEditResult(TimelineEditResultStatus.APPLIED, timeline, List.of(), Map.of());
    }

    public static TimelineEditResult validationFailed(List<TimelineValidationIssue> issues) {
        return new TimelineEditResult(TimelineEditResultStatus.VALIDATION_FAILED, null, issues, Map.of());
    }

    public static TimelineEditResult noOp(TimelineSpec timeline) {
        return new TimelineEditResult(TimelineEditResultStatus.NO_OP, timeline, List.of(), Map.of());
    }

    public static TimelineEditResult invalidOperation(List<TimelineValidationIssue> issues) {
        return new TimelineEditResult(TimelineEditResultStatus.INVALID_OPERATION, null, issues, Map.of());
    }

    public static TimelineEditResult blocked(List<TimelineValidationIssue> issues) {
        return new TimelineEditResult(TimelineEditResultStatus.BLOCKED, null, issues, Map.of());
    }

    public static TimelineEditResult failed(List<TimelineValidationIssue> issues) {
        return new TimelineEditResult(TimelineEditResultStatus.FAILED, null, issues, Map.of());
    }
}
