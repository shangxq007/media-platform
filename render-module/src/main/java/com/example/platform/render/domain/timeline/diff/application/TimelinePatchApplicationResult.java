package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.List;
import java.util.Map;

/**
 * Result of timeline patch application. Internal domain model.
 */
public record TimelinePatchApplicationResult(
        TimelinePatchApplicationStatus status,
        CanonicalTimelineSnapshot patchedSnapshot,
        List<TimelinePatchApplicationIssue> issues,
        Map<String, String> safeMetadata) {

    public static TimelinePatchApplicationResult applied(CanonicalTimelineSnapshot snapshot) {
        return new TimelinePatchApplicationResult(
                TimelinePatchApplicationStatus.APPLIED, snapshot, List.of(), Map.of());
    }

    public static TimelinePatchApplicationResult noOp(CanonicalTimelineSnapshot snapshot) {
        return new TimelinePatchApplicationResult(
                TimelinePatchApplicationStatus.NO_OP, snapshot, List.of(), Map.of());
    }

    public static TimelinePatchApplicationResult validationFailed(List<TimelinePatchApplicationIssue> issues) {
        return new TimelinePatchApplicationResult(
                TimelinePatchApplicationStatus.VALIDATION_FAILED, null, issues, Map.of());
    }

    public static TimelinePatchApplicationResult unsupported(String message) {
        return new TimelinePatchApplicationResult(
                TimelinePatchApplicationStatus.UNSUPPORTED_OPERATION, null,
                List.of(new TimelinePatchApplicationIssue(
                        TimelinePatchApplicationIssueSeverity.ERROR,
                        TimelinePatchApplicationIssueCode.UNSUPPORTED_CHANGE_TYPE,
                        "_", message, Map.of())), Map.of());
    }

    public boolean isApplied() {
        return status == TimelinePatchApplicationStatus.APPLIED;
    }
}
