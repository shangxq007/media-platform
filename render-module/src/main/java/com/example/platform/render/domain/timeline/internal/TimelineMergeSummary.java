package com.example.platform.render.domain.timeline.internal;

import java.util.List;

/**
 * Summary statistics for a completed or attempted merge operation.
 */
public record TimelineMergeSummary(
        int mergedEntities,
        int autoMergedCount,
        int conflictCount,
        int sourceChangesApplied,
        int targetChangesApplied,
        int sourceChangesRejected,
        int targetChangesRejected,
        List<String> mergedEntityIds,
        List<String> conflictedEntityIds) {

    public static TimelineMergeSummary empty() {
        return new TimelineMergeSummary(0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    public static TimelineMergeSummary merged(int sourceApplied, int targetApplied,
                                               List<String> mergedIds) {
        int total = sourceApplied + targetApplied;
        return new TimelineMergeSummary(total, total, 0, sourceApplied, targetApplied,
                0, 0, mergedIds, List.of());
    }

    public static TimelineMergeSummary conflicts(int sourceApplied, int targetApplied,
                                                  List<String> mergedIds, List<String> conflictedIds) {
        return new TimelineMergeSummary(
                sourceApplied + targetApplied, sourceApplied + targetApplied,
                conflictedIds.size(), sourceApplied, targetApplied, 0, 0,
                mergedIds, conflictedIds);
    }
}
