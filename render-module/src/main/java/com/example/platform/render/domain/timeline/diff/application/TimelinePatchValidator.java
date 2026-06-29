package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a TimelinePatch against a base snapshot. Internal domain model.
 */
public class TimelinePatchValidator {

    private static final Set<String> FORBIDDEN_PATH_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "rootPath", "relativePath",
            "materializedPath", "providerName", "providerType", "backendName",
            "executionEnvironment", "command", "process");

    public TimelinePatchValidationResult validate(
            CanonicalTimelineSnapshot base, TimelinePatch patch) {
        List<TimelinePatchApplicationIssue> issues = new ArrayList<>();

        if (base == null) {
            issues.add(issue(TimelinePatchApplicationIssueSeverity.BLOCKING,
                    TimelinePatchApplicationIssueCode.VALIDATION_FAILED,
                    "_", "Base snapshot must not be null"));
        }
        if (patch == null) {
            issues.add(issue(TimelinePatchApplicationIssueSeverity.BLOCKING,
                    TimelinePatchApplicationIssueCode.VALIDATION_FAILED,
                    "_", "Patch must not be null"));
            return TimelinePatchValidationResult.failure(issues);
        }

        // Base revision match
        if (base != null && patch.baseRevisionId() != null
                && !patch.baseRevisionId().equals(base.revisionId())) {
            issues.add(issue(TimelinePatchApplicationIssueSeverity.BLOCKING,
                    TimelinePatchApplicationIssueCode.BASE_REVISION_MISMATCH,
                    "baseRevisionId", "Patch baseRevisionId does not match base snapshot"));
        }

        // Validate operations
        if (patch.operations() != null) {
            for (TimelineChangeOperation op : patch.operations()) {
                validateOperation(op, issues);
            }
        }

        return issues.isEmpty()
                ? TimelinePatchValidationResult.success()
                : TimelinePatchValidationResult.failure(issues);
    }

    private void validateOperation(TimelineChangeOperation op,
                                    List<TimelinePatchApplicationIssue> issues) {
        if (op.type() == null) {
            issues.add(issue(TimelinePatchApplicationIssueSeverity.ERROR,
                    TimelinePatchApplicationIssueCode.INVALID_CHANGE_PATH,
                    "_", "Operation type must not be null"));
        }
        if (op.path() == null || op.path().value() == null) {
            issues.add(issue(TimelinePatchApplicationIssueSeverity.ERROR,
                    TimelinePatchApplicationIssueCode.INVALID_CHANGE_PATH,
                    "_", "Operation path must not be null"));
        } else {
            String path = op.path().value();
            if (!path.startsWith("timeline.")) {
                issues.add(issue(TimelinePatchApplicationIssueSeverity.ERROR,
                        TimelinePatchApplicationIssueCode.INVALID_CHANGE_PATH,
                        path, "Path must start with 'timeline.'"));
            }
            for (String keyword : FORBIDDEN_PATH_KEYWORDS) {
                if (path.contains(keyword)) {
                    issues.add(issue(TimelinePatchApplicationIssueSeverity.BLOCKING,
                            TimelinePatchApplicationIssueCode.STORAGE_INTERNALS_NOT_ALLOWED,
                            path, "Path contains forbidden keyword: " + keyword));
                }
            }
        }
    }

    private TimelinePatchApplicationIssue issue(
            TimelinePatchApplicationIssueSeverity severity,
            TimelinePatchApplicationIssueCode code,
            String field, String message) {
        return new TimelinePatchApplicationIssue(severity, code, field, message, java.util.Map.of());
    }
}
