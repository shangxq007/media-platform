package com.example.platform.render.domain.timeline.internal;

/**
 * Detected conflict during a timeline merge operation.
 */
public record TimelineConflict(
        String conflictId,
        EntityRef entityRef,
        TimelineConflictType conflictType,
        SemanticChange sourceChange,
        SemanticChange targetChange,
        boolean resolutionRequired,
        String message) {

    public static TimelineConflict of(EntityRef entityRef, TimelineConflictType type,
                                       SemanticChange source, SemanticChange target, String message) {
        return new TimelineConflict(
                "conflict_" + entityRef.key().replace(':', '_'),
                entityRef, type, source, target, true, message);
    }
}
