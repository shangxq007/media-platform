package com.example.platform.timeline.diff.merge;

import com.example.platform.timeline.diff.TimelineDiff;
import java.util.List;
import java.util.Map;

/**
 * Result of three-way merge conflict analysis.
 * Internal domain model. Does not merge, resolve, or persist.
 * Provider-neutral, storage-neutral.
 *
 * <p>The conflict model is the merge semantic {@link TimelineConflict}
 * (single conflict authority for the merge flow).</p>
 */
public record TimelineMergeConflictAnalysis(
        TimelineMergeConflictAnalysisId id,
        String baseRevisionId,
        String oursRevisionId,
        String theirsRevisionId,
        TimelineDiff oursDiff,
        TimelineDiff theirsDiff,
        List<TimelineConflict> conflicts,
        TimelineMergeReadiness readiness,
        TimelineMergeConflictSummary summary,
        Map<String, String> safeMetadata) {

    public TimelineMergeConflictAnalysis {
        if (id == null) throw new IllegalArgumentException("Analysis ID must not be null");
        if (readiness == null) throw new IllegalArgumentException("Readiness must not be null");
    }

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }

    public boolean isMergeReady() {
        return readiness != null && readiness.mergeReady();
    }
}
