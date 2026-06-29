package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;

/**
 * Immutable timeline commit.
 * Internal domain model. No persistence.
 *
 * <p>Initial commit may have zero parents.
 * Normal edit commit has one primary parent.
 * Manual merge commit may have two parents (future vocabulary).
 * No more than two parents. No automatic merge engine.</p>
 */
public record TimelineCommit(
        TimelineCommitId id,
        TimelineRevisionRef revisionRef,
        TimelineCommitType type,
        List<TimelineCommitParent> parents,
        TimelineCommitMetadata metadata,
        Map<String, String> safeMetadata) {

    private static final int MAX_PARENTS = 2;

    public TimelineCommit {
        if (id == null) throw new IllegalArgumentException("Commit ID must not be null");
        if (revisionRef == null) throw new IllegalArgumentException("Revision ref must not be null");
        if (type == null) throw new IllegalArgumentException("Commit type must not be null");
        if (parents == null) throw new IllegalArgumentException("Parents must not be null");
        if (parents.size() > MAX_PARENTS)
            throw new IllegalArgumentException("Commit must not have more than " + MAX_PARENTS + " parents");
        long primaryCount = parents.stream().filter(TimelineCommitParent::primaryParent).count();
        if (primaryCount > 1)
            throw new IllegalArgumentException("Commit must not have multiple primary parents");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public boolean isRoot() {
        return parents.isEmpty();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
