package com.example.platform.timeline.diff;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, strongly-typed, deterministically-ordered semantic diff result.
 * Read-only: contains no Patch, Merge, or mutation behavior.
 */
public final class TimelineChangeSet {

    private final String changeSetVersion;
    private final String productId;
    private final String baseRevisionId;
    private final String targetRevisionId;
    private final String baseContentDigest;
    private final String targetContentDigest;
    private final String timelineSchemaVersion;
    private final List<TimelineChange> changes;
    private final ChangeSummary summary;

    public TimelineChangeSet(
            String changeSetVersion,
            String productId,
            String baseRevisionId,
            String targetRevisionId,
            String baseContentDigest,
            String targetContentDigest,
            String timelineSchemaVersion,
            List<TimelineChange> changes,
            ChangeSummary summary) {
        this.changeSetVersion = Objects.requireNonNull(changeSetVersion, "changeSetVersion");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.baseRevisionId = Objects.requireNonNull(baseRevisionId, "baseRevisionId");
        this.targetRevisionId = Objects.requireNonNull(targetRevisionId, "targetRevisionId");
        this.baseContentDigest = Objects.requireNonNull(baseContentDigest, "baseContentDigest");
        this.targetContentDigest = Objects.requireNonNull(targetContentDigest, "targetContentDigest");
        this.timelineSchemaVersion = Objects.requireNonNull(timelineSchemaVersion, "timelineSchemaVersion");
        this.changes = List.copyOf(changes);
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    public String getChangeSetVersion() { return changeSetVersion; }
    public String getProductId() { return productId; }
    public String getBaseRevisionId() { return baseRevisionId; }
    public String getTargetRevisionId() { return targetRevisionId; }
    public String getBaseContentDigest() { return baseContentDigest; }
    public String getTargetContentDigest() { return targetContentDigest; }
    public String getTimelineSchemaVersion() { return timelineSchemaVersion; }
    public List<TimelineChange> getChanges() { return changes; }
    public ChangeSummary getSummary() { return summary; }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
