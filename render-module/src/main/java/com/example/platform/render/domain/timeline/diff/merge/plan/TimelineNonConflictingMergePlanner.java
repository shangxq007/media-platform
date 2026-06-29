package com.example.platform.render.domain.timeline.diff.merge.plan;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.merge.*;
import com.example.platform.render.domain.timeline.diff.merge.preview.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure, side-effect-free Timeline Non-conflicting Merge Planner.
 * Classifies operations from base/ours/theirs merge preview into
 * safe-to-apply-later, manual-review conflict, unsupported, blocked,
 * and duplicate buckets.
 *
 * <p>This planner does not apply patches, create a merged snapshot,
 * persist Timeline Git history, render media, create Products,
 * call StorageRuntime/ProductRuntime, invoke Artifact DAG,
 * use provider binding, or implement conflict resolution.</p>
 *
 * <p>Internal domain service. Provider-neutral, storage-neutral.</p>
 */
public class TimelineNonConflictingMergePlanner {

    private static final Set<String> FORBIDDEN_PATH_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "rootPath", "relativePath",
            "materializedPath", "providerName", "providerType", "backendName",
            "executionEnvironment", "command", "process",
            "providerBindingPlanId", "renderExecutionPlanId",
            "artifactGraphId", "capabilityGraphId");

    private final TimelineMergePreviewService previewService;

    public TimelineNonConflictingMergePlanner(TimelineMergePreviewService previewService) {
        if (previewService == null)
            throw new IllegalArgumentException("Preview service must not be null");
        this.previewService = previewService;
    }

    /**
     * Generate a non-conflicting merge plan from the given request.
     *
     * @param request the merge plan request containing base/ours/theirs snapshots and policy
     * @return a merge plan classifying operations into safe/conflict/unsupported/blocked/duplicate
     */
    public TimelineNonConflictingMergePlan plan(TimelineMergePlanRequest request) {
        if (request == null) {
            return TimelineNonConflictingMergePlan.invalidInput(
                    planId("plan-invalid"),
                    List.of(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                            TimelineMergePlanIssueCode.INVALID_REQUEST,
                            "request", "Request must not be null")));
        }

        if (request.id() == null) {
            return TimelineNonConflictingMergePlan.invalidInput(
                    planId("plan-invalid-id"),
                    List.of(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                            TimelineMergePlanIssueCode.INVALID_REQUEST,
                            "id", "Request ID must not be null")));
        }

        // Validate base/ours/theirs
        List<TimelineMergePlanIssue> inputIssues = validateInputs(request);
        if (!inputIssues.isEmpty()) {
            return TimelineNonConflictingMergePlan.invalidInput(
                    planId("plan-invalid-input"), inputIssues);
        }

        // Build preview request using DIFF_AND_CONFLICTS mode to get full analysis
        TimelineMergePreviewRequest previewRequest = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId(request.id().value() + "-preview"),
                request.base(), request.ours(), request.theirs(),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                mapPolicy(request.effectivePolicy()),
                request.safeMetadata());

        TimelineMergePreviewResult previewResult;
        try {
            previewResult = previewService.preview(previewRequest);
        } catch (Exception e) {
            return TimelineNonConflictingMergePlan.failed(
                    planId("plan-preview-exception"),
                    List.of(issue(TimelineMergePlanIssueSeverity.ERROR,
                            TimelineMergePlanIssueCode.PREVIEW_FAILED,
                            "_", "Preview failed due to an internal error")));
        }

        // Map preview status to plan status for non-analysis cases
        if (previewResult.status() == TimelineMergePreviewStatus.INVALID_INPUT) {
            return TimelineNonConflictingMergePlan.invalidInput(
                    planId("plan-preview-invalid"), mapIssues(previewResult.issues()));
        }
        if (previewResult.status() == TimelineMergePreviewStatus.BLOCKED) {
            return TimelineNonConflictingMergePlan.blocked(
                    planId("plan-preview-blocked"), mapIssues(previewResult.issues()));
        }
        if (previewResult.status() == TimelineMergePreviewStatus.UNSUPPORTED) {
            return TimelineNonConflictingMergePlan.unsupported(
                    planId("plan-preview-unsupported"), mapIssues(previewResult.issues()));
        }
        if (previewResult.status() == TimelineMergePreviewStatus.FAILED) {
            return TimelineNonConflictingMergePlan.failed(
                    planId("plan-preview-failed"), mapIssues(previewResult.issues()));
        }

        // Extract conflict analysis
        TimelineMergeConflictAnalysis analysis = previewResult.conflictAnalysis();
        if (analysis == null) {
            return TimelineNonConflictingMergePlan.failed(
                    planId("plan-no-analysis"),
                    List.of(issue(TimelineMergePlanIssueSeverity.ERROR,
                            TimelineMergePlanIssueCode.PREVIEW_FAILED,
                            "_", "Preview returned no conflict analysis")));
        }

        // Classify operations
        TimelineMergePlanPolicy effectivePolicy = request.effectivePolicy();
        List<TimelineMergePlanOperation> operations = new ArrayList<>();
        List<TimelineMergePlanIssue> planIssues = new ArrayList<>();

        // Collect conflicting paths from conflicts
        Set<String> conflictPaths = new HashSet<>();
        if (analysis.conflicts() != null) {
            for (TimelineConflict conflict : analysis.conflicts()) {
                if (conflict.path() != null) {
                    conflictPaths.add(conflict.path().value());
                }
            }
        }

        // Process ours operations
        Map<String, List<TimelineChangeOperation>> oursByPath = groupByPath(
                analysis.oursDiff() != null ? analysis.oursDiff().operations() : List.of());
        Map<String, List<TimelineChangeOperation>> theirsByPath = groupByPath(
                analysis.theirsDiff() != null ? analysis.theirsDiff().operations() : List.of());

        Set<String> allPaths = new LinkedHashSet<>();
        allPaths.addAll(oursByPath.keySet());
        allPaths.addAll(theirsByPath.keySet());

        for (String path : allPaths) {
            List<TimelineChangeOperation> oursOps = oursByPath.getOrDefault(path, List.of());
            List<TimelineChangeOperation> theirsOps = theirsByPath.getOrDefault(path, List.of());

            // Check for forbidden paths
            if (isForbiddenPath(path)) {
                for (TimelineChangeOperation op : concatOps(oursOps, theirsOps)) {
                    operations.add(TimelineMergePlanOperation.blocked(op,
                            issue(TimelineMergePlanIssueSeverity.BLOCKING,
                                    TimelineMergePlanIssueCode.BLOCKED_OPERATION,
                                    path, "Path contains forbidden keyword")));
                    planIssues.add(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                            TimelineMergePlanIssueCode.BLOCKED_OPERATION,
                            path, "Path contains forbidden keyword"));
                }
                continue;
            }

            boolean oursEmpty = oursOps.isEmpty();
            boolean theirsEmpty = theirsOps.isEmpty();

            if (!oursEmpty && !theirsEmpty) {
                // Both sides touched this path
                if (areIdenticalChanges(oursOps, theirsOps)) {
                    // Identical same-path change
                    if (effectivePolicy == TimelineMergePlanPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES) {
                        // Skip as duplicate
                        for (TimelineChangeOperation op : oursOps) {
                            operations.add(TimelineMergePlanOperation.skippedDuplicate(
                                    op, TimelineMergePlanOperationSource.BOTH_IDENTICAL));
                        }
                    } else {
                        // Mark as safe (identical, no conflict)
                        for (TimelineChangeOperation op : oursOps) {
                            operations.add(new TimelineMergePlanOperation(
                                    TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER,
                                    TimelineMergePlanOperationSource.BOTH_IDENTICAL,
                                    op, path, List.of(), List.of(), Map.of()));
                        }
                    }
                } else {
                    // Divergent same-path change — conflict
                    if (effectivePolicy == TimelineMergePlanPolicy.BLOCK_ON_ANY_CONFLICT) {
                        // For BLOCK_ON_ANY_CONFLICT, mark as blocked
                        for (TimelineChangeOperation op : oursOps) {
                            operations.add(TimelineMergePlanOperation.blocked(op,
                                    issue(TimelineMergePlanIssueSeverity.BLOCKING,
                                            TimelineMergePlanIssueCode.BLOCKED_OPERATION,
                                            path, "BLOCK_ON_ANY_CONFLICT: divergent change")));
                        }
                        for (TimelineChangeOperation op : theirsOps) {
                            operations.add(TimelineMergePlanOperation.blocked(op,
                                    issue(TimelineMergePlanIssueSeverity.BLOCKING,
                                            TimelineMergePlanIssueCode.BLOCKED_OPERATION,
                                            path, "BLOCK_ON_ANY_CONFLICT: divergent change")));
                        }
                    } else {
                        // Find related conflicts for this path
                        List<TimelineConflict> relatedConflicts = analysis.conflicts().stream()
                                .filter(c -> c.path() != null && path.equals(c.path().value()))
                                .collect(Collectors.toList());

                        for (TimelineChangeOperation op : oursOps) {
                            operations.add(TimelineMergePlanOperation.conflict(
                                    TimelineMergePlanOperationSource.OURS, op,
                                    relatedConflicts,
                                    List.of(issue(TimelineMergePlanIssueSeverity.WARNING,
                                            TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                                            path, "Divergent change requires manual review"))));
                        }
                        for (TimelineChangeOperation op : theirsOps) {
                            operations.add(TimelineMergePlanOperation.conflict(
                                    TimelineMergePlanOperationSource.THEIRS, op,
                                    relatedConflicts,
                                    List.of(issue(TimelineMergePlanIssueSeverity.WARNING,
                                            TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                                            path, "Divergent change requires manual review"))));
                        }
                    }
                    planIssues.add(issue(TimelineMergePlanIssueSeverity.WARNING,
                            TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                            path, "Divergent change on path: " + path));
                }
            } else if (!oursEmpty) {
                // Only ours touched this path — check if it's related to a conflict
                if (conflictPaths.contains(path)) {
                    List<TimelineConflict> relatedConflicts = analysis.conflicts().stream()
                            .filter(c -> c.path() != null && path.equals(c.path().value()))
                            .collect(Collectors.toList());
                    for (TimelineChangeOperation op : oursOps) {
                        operations.add(TimelineMergePlanOperation.conflict(
                                TimelineMergePlanOperationSource.OURS, op,
                                relatedConflicts,
                                List.of(issue(TimelineMergePlanIssueSeverity.WARNING,
                                        TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                                        path, "Operation related to conflict"))));
                    }
                    planIssues.add(issue(TimelineMergePlanIssueSeverity.WARNING,
                            TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                            path, "Ours operation related to conflict: " + path));
                } else {
                    for (TimelineChangeOperation op : oursOps) {
                        operations.add(TimelineMergePlanOperation.safeFromOurs(op));
                    }
                }
            } else {
                // Only theirs touched this path
                if (conflictPaths.contains(path)) {
                    List<TimelineConflict> relatedConflicts = analysis.conflicts().stream()
                            .filter(c -> c.path() != null && path.equals(c.path().value()))
                            .collect(Collectors.toList());
                    for (TimelineChangeOperation op : theirsOps) {
                        operations.add(TimelineMergePlanOperation.conflict(
                                TimelineMergePlanOperationSource.THEIRS, op,
                                relatedConflicts,
                                List.of(issue(TimelineMergePlanIssueSeverity.WARNING,
                                        TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                                        path, "Operation related to conflict"))));
                    }
                    planIssues.add(issue(TimelineMergePlanIssueSeverity.WARNING,
                            TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW,
                            path, "Theirs operation related to conflict: " + path));
                } else {
                    for (TimelineChangeOperation op : theirsOps) {
                        operations.add(TimelineMergePlanOperation.safeFromTheirs(op));
                    }
                }
            }
        }

        // Sort operations deterministically
        operations.sort(Comparator
                .comparingInt((TimelineMergePlanOperation op) -> operationStatusOrder(op.status()))
                .thenComparingInt(op -> operationSourceOrder(op.source()))
                .thenComparingInt(op -> op.operation() != null && op.operation().type() != null
                        ? op.operation().type().ordinal() : Integer.MAX_VALUE)
                .thenComparing(op -> op.path() != null ? op.path() : ""));

        // Build summary
        long safeCount = operations.stream()
                .filter(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER)
                .count();
        long conflictCount = operations.stream()
                .filter(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW)
                .count();
        long unsupportedCount = operations.stream()
                .filter(op -> op.status() == TimelineMergePlanOperationStatus.UNSUPPORTED)
                .count();
        long blockedCount = operations.stream()
                .filter(op -> op.status() == TimelineMergePlanOperationStatus.BLOCKED)
                .count();
        long skippedCount = operations.stream()
                .filter(op -> op.status() == TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE)
                .count();

        int oursOpsCount = analysis.oursDiff() != null ? analysis.oursDiff().operations().size() : 0;
        int theirsOpsCount = analysis.theirsDiff() != null ? analysis.theirsDiff().operations().size() : 0;
        int conflictCountFromAnalysis = analysis.conflicts() != null ? analysis.conflicts().size() : 0;
        boolean manualReview = conflictCount > 0 || blockedCount > 0;
        boolean canAutoApply = conflictCount == 0 && unsupportedCount == 0 && blockedCount == 0;

        TimelineMergePlanSummary summary = TimelineMergePlanSummary.of(
                oursOpsCount, theirsOpsCount,
                (int) safeCount, (int) conflictCount, (int) unsupportedCount,
                (int) blockedCount, (int) skippedCount,
                conflictCountFromAnalysis, manualReview, canAutoApply);

        // Determine plan status
        TimelineMergePlanStatus planStatus;
        if (effectivePolicy == TimelineMergePlanPolicy.BLOCK_ON_ANY_CONFLICT && conflictCountFromAnalysis > 0) {
            planStatus = TimelineMergePlanStatus.BLOCKED;
        } else if (blockedCount > 0) {
            planStatus = TimelineMergePlanStatus.BLOCKED;
        } else if (conflictCount > 0) {
            planStatus = TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED;
        } else if (unsupportedCount > 0) {
            planStatus = TimelineMergePlanStatus.UNSUPPORTED;
        } else {
            planStatus = TimelineMergePlanStatus.READY;
        }

        TimelineMergePlanId planId = planId("plan-" + request.id().value());

        return new TimelineNonConflictingMergePlan(
                planId, planStatus, previewResult, operations, summary, planIssues, Map.of());
    }

    // ===== Internal =====

    private List<TimelineMergePlanIssue> validateInputs(TimelineMergePlanRequest request) {
        List<TimelineMergePlanIssue> issues = new ArrayList<>();
        if (request.base() == null) {
            issues.add(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                    TimelineMergePlanIssueCode.MISSING_BASE,
                    "base", "Base snapshot must not be null"));
        }
        if (request.ours() == null) {
            issues.add(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                    TimelineMergePlanIssueCode.MISSING_OURS,
                    "ours", "Ours snapshot must not be null"));
        }
        if (request.theirs() == null) {
            issues.add(issue(TimelineMergePlanIssueSeverity.BLOCKING,
                    TimelineMergePlanIssueCode.MISSING_THEIRS,
                    "theirs", "Theirs snapshot must not be null"));
        }
        return issues;
    }

    private boolean areIdenticalChanges(List<TimelineChangeOperation> oursOps,
                                         List<TimelineChangeOperation> theirsOps) {
        if (oursOps.size() != theirsOps.size()) return false;
        for (int i = 0; i < oursOps.size(); i++) {
            TimelineChangeOperation oo = oursOps.get(i);
            TimelineChangeOperation to = theirsOps.get(i);
            if (oo.type() != to.type()) return false;
            String oursAfter = oo.afterValue() != null ? oo.afterValue().stringValue() : null;
            String theirsAfter = to.afterValue() != null ? to.afterValue().stringValue() : null;
            if (!Objects.equals(oursAfter, theirsAfter)) return false;
        }
        return true;
    }

    private Map<String, List<TimelineChangeOperation>> groupByPath(List<TimelineChangeOperation> ops) {
        Map<String, List<TimelineChangeOperation>> map = new LinkedHashMap<>();
        if (ops != null) {
            for (TimelineChangeOperation op : ops) {
                if (op.path() != null && op.path().value() != null) {
                    map.computeIfAbsent(op.path().value(), k -> new ArrayList<>()).add(op);
                }
            }
        }
        return map;
    }

    private boolean isForbiddenPath(String path) {
        if (path == null) return false;
        for (String keyword : FORBIDDEN_PATH_KEYWORDS) {
            if (path.contains(keyword)) return true;
        }
        return false;
    }

    private List<TimelineChangeOperation> concatOps(
            List<TimelineChangeOperation> a, List<TimelineChangeOperation> b) {
        List<TimelineChangeOperation> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private TimelineMergePreviewPolicy mapPolicy(TimelineMergePlanPolicy policy) {
        return switch (policy) {
            case CONSERVATIVE -> TimelineMergePreviewPolicy.CONSERVATIVE;
            case ALLOW_DIFFERENT_PATHS -> TimelineMergePreviewPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES;
            case ALLOW_IDENTICAL_SAME_PATH_CHANGES -> TimelineMergePreviewPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES;
            case BLOCK_ON_ANY_CONFLICT -> TimelineMergePreviewPolicy.BLOCK_ON_ANY_CONFLICT;
        };
    }

    private List<TimelineMergePlanIssue> mapIssues(List<TimelineMergePreviewIssue> previewIssues) {
        if (previewIssues == null) return List.of();
        return previewIssues.stream()
                .map(pi -> issue(
                        mapSeverity(pi.severity()),
                        mapCode(pi.code()),
                        pi.field(),
                        pi.message()))
                .collect(Collectors.toList());
    }

    private TimelineMergePlanIssueSeverity mapSeverity(TimelineMergePreviewIssueSeverity severity) {
        return switch (severity) {
            case INFO -> TimelineMergePlanIssueSeverity.INFO;
            case WARNING -> TimelineMergePlanIssueSeverity.WARNING;
            case ERROR -> TimelineMergePlanIssueSeverity.ERROR;
            case BLOCKING -> TimelineMergePlanIssueSeverity.BLOCKING;
        };
    }

    private TimelineMergePlanIssueCode mapCode(TimelineMergePreviewIssueCode code) {
        return switch (code) {
            case MISSING_BASE -> TimelineMergePlanIssueCode.MISSING_BASE;
            case MISSING_OURS -> TimelineMergePlanIssueCode.MISSING_OURS;
            case MISSING_THEIRS -> TimelineMergePlanIssueCode.MISSING_THEIRS;
            case INVALID_REQUEST -> TimelineMergePlanIssueCode.INVALID_REQUEST;
            case CONFLICT_ANALYSIS_FAILED -> TimelineMergePlanIssueCode.PREVIEW_FAILED;
            case MANUAL_REVIEW_REQUIRED -> TimelineMergePlanIssueCode.CONFLICT_REQUIRES_MANUAL_REVIEW;
            case UNSUPPORTED_PREVIEW_MODE -> TimelineMergePlanIssueCode.UNSUPPORTED_OPERATION;
            case PROVIDER_INTERNALS_NOT_ALLOWED -> TimelineMergePlanIssueCode.PROVIDER_INTERNALS_NOT_ALLOWED;
            case STORAGE_INTERNALS_NOT_ALLOWED -> TimelineMergePlanIssueCode.STORAGE_INTERNALS_NOT_ALLOWED;
            case EXECUTION_NOT_ALLOWED -> TimelineMergePlanIssueCode.BLOCKED_OPERATION;
        };
    }

    private int operationStatusOrder(TimelineMergePlanOperationStatus status) {
        return switch (status) {
            case BLOCKED -> 0;
            case CONFLICT_REQUIRES_MANUAL_REVIEW -> 1;
            case UNSUPPORTED -> 2;
            case SAFE_TO_APPLY_LATER -> 3;
            case SKIPPED_DUPLICATE -> 4;
        };
    }

    private int operationSourceOrder(TimelineMergePlanOperationSource source) {
        return switch (source) {
            case OURS -> 0;
            case THEIRS -> 1;
            case BOTH_IDENTICAL -> 2;
            case SYSTEM -> 3;
        };
    }

    private TimelineMergePlanId planId(String value) {
        return new TimelineMergePlanId(value);
    }

    private TimelineMergePlanIssue issue(
            TimelineMergePlanIssueSeverity severity,
            TimelineMergePlanIssueCode code,
            String field, String message) {
        return new TimelineMergePlanIssue(severity, code, field, message, Map.of());
    }
}
