package com.example.platform.timeline.diff;

import java.util.Objects;

/**
 * Immutable, strongly-typed single change in a TimelineChangeSet.
 * Read-only: no Patch, Merge, or mutation behavior.
 */
public final class TimelineChange {

    private final ChangeType changeType;
    private final EntityKind entityKind;
    private final String entityId;
    private final String propertyName;
    private final String beforeValue;
    private final String afterValue;
    private final int targetPosition;

    public TimelineChange(
            ChangeType changeType,
            EntityKind entityKind,
            String entityId,
            String propertyName,
            String beforeValue,
            String afterValue,
            int targetPosition) {
        this.changeType = Objects.requireNonNull(changeType, "changeType");
        this.entityKind = Objects.requireNonNull(entityKind, "entityKind");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.propertyName = propertyName;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.targetPosition = targetPosition;
    }

    public ChangeType getChangeType() { return changeType; }
    public EntityKind getEntityKind() { return entityKind; }
    public String getEntityId() { return entityId; }
    public String getPropertyName() { return propertyName; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public int getTargetPosition() { return targetPosition; }

    public static TimelineChange added(EntityKind kind, String entityId, int position) {
        ChangeType type = kind == EntityKind.TRACK ? ChangeType.TRACK_ADDED : ChangeType.CLIP_ADDED;
        return new TimelineChange(type, kind, entityId, null, null, null, position);
    }

    public static TimelineChange removed(EntityKind kind, String entityId, int position) {
        ChangeType type = kind == EntityKind.TRACK ? ChangeType.TRACK_REMOVED : ChangeType.CLIP_REMOVED;
        return new TimelineChange(type, kind, entityId, null, null, null, position);
    }

    public static TimelineChange propertyChanged(EntityKind kind, String entityId, String property, String before, String after) {
        ChangeType type = kind == EntityKind.TRACK ? ChangeType.TRACK_PROPERTY_CHANGED : ChangeType.CLIP_PROPERTY_CHANGED;
        return new TimelineChange(type, kind, entityId, property, before, after, -1);
    }

    public static TimelineChange reordered(EntityKind kind, String entityId, int newPosition) {
        ChangeType type = kind == EntityKind.TRACK ? ChangeType.TRACK_REORDERED : ChangeType.CLIP_REORDERED;
        return new TimelineChange(type, kind, entityId, null, null, null, newPosition);
    }

    /** Typed relationship semantic change (SEMANTIC_RELATIONSHIP_SELECTION_POST_CLOSE). */
    public static TimelineChange relationshipChanged(ChangeType type, String entityId,
                                                     String property, String before, String after) {
        return new TimelineChange(type, EntityKind.CLIP, entityId, property, before, after, 0);
    }

    public static TimelineChange moved(String entityId, String fromTrackId, String toTrackId) {
        return new TimelineChange(ChangeType.CLIP_MOVED, EntityKind.CLIP, entityId, "trackId", fromTrackId, toTrackId, -1);
    }
}
