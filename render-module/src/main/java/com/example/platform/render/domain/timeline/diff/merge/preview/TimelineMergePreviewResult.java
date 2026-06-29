package com.example.platform.render.domain.timeline.diff.merge.preview;

import com.example.platform.render.domain.timeline.diff.merge.TimelineMergeConflictAnalysis;
import java.util.List;
import java.util.Map;

/**
 * Result of a merge preview.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePreviewResult(
        TimelineMergePreviewStatus status,
        TimelineMergePreviewSummary summary,
        TimelineMergeConflictAnalysis conflictAnalysis,
        List<TimelineMergePreviewIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineMergePreviewResult {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
    }

    public boolean isMergeReady() {
        return status == TimelineMergePreviewStatus.MERGE_READY;
    }

    public boolean hasConflicts() {
        return conflictAnalysis != null && conflictAnalysis.hasConflicts();
    }

    /**
     * Factory: merge-ready result with analysis.
     */
    public static TimelineMergePreviewResult mergeReady(
            TimelineMergePreviewSummary summary,
            TimelineMergeConflictAnalysis analysis) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.MERGE_READY, summary, analysis, List.of(), Map.of());
    }

    /**
     * Factory: manual-review result with analysis and issues.
     */
    public static TimelineMergePreviewResult manualReview(
            TimelineMergePreviewSummary summary,
            TimelineMergeConflictAnalysis analysis,
            List<TimelineMergePreviewIssue> issues) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, summary, analysis, issues, Map.of());
    }

    /**
     * Factory: blocked result with issues.
     */
    public static TimelineMergePreviewResult blocked(
            List<TimelineMergePreviewIssue> issues) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.BLOCKED, null, null, issues, Map.of());
    }

    /**
     * Factory: invalid-input result with issues.
     */
    public static TimelineMergePreviewResult invalidInput(
            List<TimelineMergePreviewIssue> issues) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.INVALID_INPUT, null, null, issues, Map.of());
    }

    /**
     * Factory: unsupported result with issues.
     */
    public static TimelineMergePreviewResult unsupported(
            List<TimelineMergePreviewIssue> issues) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.UNSUPPORTED, null, null, issues, Map.of());
    }

    /**
     * Factory: failed result with safe issue (no stack trace).
     */
    public static TimelineMergePreviewResult failed(
            List<TimelineMergePreviewIssue> issues) {
        return new TimelineMergePreviewResult(
                TimelineMergePreviewStatus.FAILED, null, null, issues, Map.of());
    }
}
