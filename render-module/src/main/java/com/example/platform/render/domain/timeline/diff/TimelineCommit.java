package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Immutable timeline version snapshot — commit-like object.
 * Internal domain model. No persistence, no object store.
 */
public record TimelineCommit(
        TimelineCommitId id,
        List<TimelineCommitParent> parents,
        TimelineSemanticHash semanticHash,
        String revisionId,
        String authorType,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineCommit {
        if (id == null) throw new IllegalArgumentException("Commit ID must not be null");
        if (parents == null) throw new IllegalArgumentException("Parents must not be null");
    }

    public boolean isRoot() {
        return parents.isEmpty();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
