package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Semantic diff between two timeline versions.
 * Internal domain model. Does not compute diff — stores diff result.
 * Provider-neutral, storage-neutral, tenant-safe.
 */
public record TimelineDiff(
        TimelineDiffId id,
        String baseRevisionId,
        String targetRevisionId,
        List<TimelineChangeOperation> operations,
        List<TimelineConflict> conflicts,
        TimelineRenderImpact renderImpact,
        Map<String, String> safeMetadata) {

    public TimelineDiff {
        if (id == null) throw new IllegalArgumentException("Diff ID must not be null");
        if (baseRevisionId == null || baseRevisionId.isBlank())
            throw new IllegalArgumentException("Base revision ID must not be blank");
        if (targetRevisionId == null || targetRevisionId.isBlank())
            throw new IllegalArgumentException("Target revision ID must not be blank");
    }

    public boolean isNoOp() {
        return operations == null || operations.isEmpty();
    }

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }

    public boolean hasBlockingConflicts() {
        return conflicts != null && conflicts.stream().anyMatch(TimelineConflict::isBlocking);
    }
}
