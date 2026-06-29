package com.example.platform.render.domain.timeline.diff.merge.preview;

import com.example.platform.render.domain.timeline.diff.merge.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, side-effect-free Timeline Merge Preview Service.
 * Delegates conflict analysis to {@link TimelineMergeConflictDetector}.
 *
 * <p>This service does not merge, resolve conflicts, apply patches, or persist.
 * It produces a safe preview result for future UI/API/application services.</p>
 *
 * <p>Internal domain service. Provider-neutral, storage-neutral.</p>
 */
public class TimelineMergePreviewService {

    private static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "bucket", "objectKey", "signedUrl", "rootPath", "relativePath",
            "materializedPath", "providerName", "providerType", "backendName",
            "executionEnvironment", "autoDispatch");

    private final TimelineMergeConflictDetector conflictDetector;

    public TimelineMergePreviewService(TimelineMergeConflictDetector conflictDetector) {
        if (conflictDetector == null)
            throw new IllegalArgumentException("Conflict detector must not be null");
        this.conflictDetector = conflictDetector;
    }

    /**
     * Produce a merge preview from the given request.
     *
     * @param request the preview request containing base/ours/theirs snapshots, mode, and policy
     * @return a preview result with status, summary, conflict analysis, and issues
     */
    public TimelineMergePreviewResult preview(TimelineMergePreviewRequest request) {
        if (request == null) {
            return TimelineMergePreviewResult.invalidInput(List.of(
                    TimelineMergePreviewIssue.of(
                            TimelineMergePreviewIssueSeverity.BLOCKING,
                            TimelineMergePreviewIssueCode.INVALID_REQUEST,
                            "request",
                            "Request must not be null")));
        }

        // Validate request id
        if (request.id() == null) {
            return TimelineMergePreviewResult.invalidInput(List.of(
                    TimelineMergePreviewIssue.of(
                            TimelineMergePreviewIssueSeverity.BLOCKING,
                            TimelineMergePreviewIssueCode.INVALID_REQUEST,
                            "id",
                            "Request ID must not be null")));
        }

        // Validate base/ours/theirs
        List<TimelineMergePreviewIssue> inputIssues = validateInputs(request);
        if (!inputIssues.isEmpty()) {
            return TimelineMergePreviewResult.invalidInput(inputIssues);
        }

        // Check for forbidden metadata keys in request safeMetadata
        if (request.safeMetadata() != null) {
            List<TimelineMergePreviewIssue> forbiddenIssues = checkForbiddenMetadata(request.safeMetadata());
            if (!forbiddenIssues.isEmpty()) {
                return TimelineMergePreviewResult.blocked(forbiddenIssues);
            }
        }

        try {
            // Delegate to conflict detector
            TimelineMergeConflictAnalysis analysis = conflictDetector.analyze(
                    request.base(), request.ours(), request.theirs());

            // Map readiness status to preview status
            TimelineMergePreviewStatus status = mapReadinessStatus(analysis.readiness().status());

            // Build summary
            TimelineMergePreviewSummary summary = buildSummary(analysis);

            // Build preview issues from conflict analysis readiness issues
            List<TimelineMergePreviewIssue> previewIssues = buildPreviewIssues(analysis, request.effectivePolicy());

            // Adjust status based on policy
            status = applyPolicy(status, analysis, request.effectivePolicy());

            // Build result based on mode
            return buildResult(status, summary, analysis, previewIssues, request.effectiveMode());

        } catch (Exception e) {
            // Convert any unexpected exception to safe FAILED result (no stack trace, no class names)
            return TimelineMergePreviewResult.failed(List.of(
                    TimelineMergePreviewIssue.of(
                            TimelineMergePreviewIssueSeverity.ERROR,
                            TimelineMergePreviewIssueCode.CONFLICT_ANALYSIS_FAILED,
                            "_",
                            "Preview failed due to an internal error")));
        }
    }

    // ===== Internal =====

    private List<TimelineMergePreviewIssue> validateInputs(TimelineMergePreviewRequest request) {
        List<TimelineMergePreviewIssue> issues = new ArrayList<>();
        if (request.base() == null) {
            issues.add(TimelineMergePreviewIssue.of(
                    TimelineMergePreviewIssueSeverity.BLOCKING,
                    TimelineMergePreviewIssueCode.MISSING_BASE,
                    "base",
                    "Base snapshot must not be null"));
        }
        if (request.ours() == null) {
            issues.add(TimelineMergePreviewIssue.of(
                    TimelineMergePreviewIssueSeverity.BLOCKING,
                    TimelineMergePreviewIssueCode.MISSING_OURS,
                    "ours",
                    "Ours snapshot must not be null"));
        }
        if (request.theirs() == null) {
            issues.add(TimelineMergePreviewIssue.of(
                    TimelineMergePreviewIssueSeverity.BLOCKING,
                    TimelineMergePreviewIssueCode.MISSING_THEIRS,
                    "theirs",
                    "Theirs snapshot must not be null"));
        }
        return issues;
    }

    private List<TimelineMergePreviewIssue> checkForbiddenMetadata(Map<String, String> metadata) {
        List<TimelineMergePreviewIssue> issues = new ArrayList<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            for (String forbidden : FORBIDDEN_METADATA_KEYS) {
                if (key.contains(forbidden) || (value != null && value.contains(forbidden))) {
                    issues.add(TimelineMergePreviewIssue.of(
                            TimelineMergePreviewIssueSeverity.BLOCKING,
                            TimelineMergePreviewIssueCode.STORAGE_INTERNALS_NOT_ALLOWED,
                            key,
                            "Metadata contains forbidden keyword: " + forbidden));
                }
            }
        }
        return issues;
    }

    private TimelineMergePreviewStatus mapReadinessStatus(TimelineMergeReadinessStatus readinessStatus) {
        return switch (readinessStatus) {
            case MERGE_READY -> TimelineMergePreviewStatus.MERGE_READY;
            case MANUAL_REVIEW_REQUIRED -> TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED;
            case BLOCKED -> TimelineMergePreviewStatus.BLOCKED;
            case UNSUPPORTED -> TimelineMergePreviewStatus.UNSUPPORTED;
            case INVALID_INPUT -> TimelineMergePreviewStatus.INVALID_INPUT;
        };
    }

    private TimelineMergePreviewSummary buildSummary(TimelineMergeConflictAnalysis analysis) {
        return TimelineMergePreviewSummary.of(
                analysis.baseRevisionId(),
                analysis.oursRevisionId(),
                analysis.theirsRevisionId(),
                analysis.summary().oursOperationCount(),
                analysis.summary().theirsOperationCount(),
                analysis.summary().conflictCount(),
                analysis.summary().blockingConflictCount(),
                analysis.readiness().mergeReady(),
                analysis.readiness().manualReviewRequired());
    }

    private List<TimelineMergePreviewIssue> buildPreviewIssues(
            TimelineMergeConflictAnalysis analysis, TimelineMergePreviewPolicy policy) {
        List<TimelineMergePreviewIssue> issues = new ArrayList<>();

        // Convert readiness issues to preview issues
        if (analysis.readiness().issues() != null) {
            for (TimelineMergeConflictIssue ci : analysis.readiness().issues()) {
                issues.add(TimelineMergePreviewIssue.of(
                        mapIssueSeverity(ci.severity()),
                        mapIssueCode(ci.code()),
                        ci.field(),
                        ci.message()));
            }
        }

        // Add policy-specific issues
        if (policy == TimelineMergePreviewPolicy.BLOCK_ON_ANY_CONFLICT
                && analysis.hasConflicts()) {
            issues.add(TimelineMergePreviewIssue.of(
                    TimelineMergePreviewIssueSeverity.WARNING,
                    TimelineMergePreviewIssueCode.MANUAL_REVIEW_REQUIRED,
                    "policy",
                    "BLOCK_ON_ANY_CONFLICT policy: conflicts detected"));
        }

        return issues;
    }

    private TimelineMergePreviewStatus applyPolicy(
            TimelineMergePreviewStatus currentStatus,
            TimelineMergeConflictAnalysis analysis,
            TimelineMergePreviewPolicy policy) {
        return switch (policy) {
            case CONSERVATIVE -> currentStatus;
            case ALLOW_IDENTICAL_SAME_PATH_CHANGES -> {
                // If all conflicts are identical same-path changes, downgrade to MERGE_READY
                // This is vocabulary-only in P2V.4 — service delegates readiness to detector
                yield currentStatus;
            }
            case BLOCK_ON_ANY_CONFLICT -> {
                if (analysis.hasConflicts()) {
                    yield TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED;
                }
                yield currentStatus;
            }
        };
    }

    private TimelineMergePreviewResult buildResult(
            TimelineMergePreviewStatus status,
            TimelineMergePreviewSummary summary,
            TimelineMergeConflictAnalysis analysis,
            List<TimelineMergePreviewIssue> issues,
            TimelineMergePreviewMode mode) {
        return switch (mode) {
            case READINESS_ONLY -> new TimelineMergePreviewResult(
                    status, summary, null, issues, Map.of());
            case CONFLICTS_ONLY -> new TimelineMergePreviewResult(
                    status, summary, analysis, issues, Map.of());
            case DIFF_AND_CONFLICTS -> new TimelineMergePreviewResult(
                    status, summary, analysis, issues, Map.of());
        };
    }

    private TimelineMergePreviewIssueSeverity mapIssueSeverity(
            TimelineMergeConflictIssueSeverity severity) {
        return switch (severity) {
            case INFO -> TimelineMergePreviewIssueSeverity.INFO;
            case WARNING -> TimelineMergePreviewIssueSeverity.WARNING;
            case ERROR -> TimelineMergePreviewIssueSeverity.ERROR;
            case BLOCKING -> TimelineMergePreviewIssueSeverity.BLOCKING;
        };
    }

    private TimelineMergePreviewIssueCode mapIssueCode(
            TimelineMergeConflictIssueCode code) {
        return switch (code) {
            case MISSING_BASE -> TimelineMergePreviewIssueCode.MISSING_BASE;
            case MISSING_OURS -> TimelineMergePreviewIssueCode.MISSING_OURS;
            case MISSING_THEIRS -> TimelineMergePreviewIssueCode.MISSING_THEIRS;
            case PROVIDER_INTERNALS_NOT_ALLOWED -> TimelineMergePreviewIssueCode.PROVIDER_INTERNALS_NOT_ALLOWED;
            case STORAGE_INTERNALS_NOT_ALLOWED -> TimelineMergePreviewIssueCode.STORAGE_INTERNALS_NOT_ALLOWED;
            case EXECUTION_NOT_ALLOWED -> TimelineMergePreviewIssueCode.EXECUTION_NOT_ALLOWED;
            default -> TimelineMergePreviewIssueCode.MANUAL_REVIEW_REQUIRED;
        };
    }
}
