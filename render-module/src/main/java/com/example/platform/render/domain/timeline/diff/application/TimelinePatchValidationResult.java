package com.example.platform.render.domain.timeline.diff.application;

import java.util.List;

public record TimelinePatchValidationResult(
        boolean valid,
        List<TimelinePatchApplicationIssue> issues) {

    public static TimelinePatchValidationResult success() {
        return new TimelinePatchValidationResult(true, List.of());
    }

    public static TimelinePatchValidationResult failure(List<TimelinePatchApplicationIssue> issues) {
        return new TimelinePatchValidationResult(false, issues);
    }
}
