package com.example.platform.render.domain.timeline.internal;

/**
 * User intent for resolving a timeline merge conflict.
 *
 * <p>Expresses which side to use when two branches made incompatible changes
 * to the same entity. Does not include UI or persistence — this is a
 * pure domain value object.</p>
 */
public record TimelineResolutionIntent(
        EntityRef entityRef,
        TimelineConflictType conflictType,
        ResolutionMode resolutionMode,
        String metadata) {

    public enum ResolutionMode {
        USE_SOURCE,
        USE_TARGET,
        MANUAL
    }

    public static TimelineResolutionIntent useSource(EntityRef entityRef, TimelineConflictType type) {
        return new TimelineResolutionIntent(entityRef, type, ResolutionMode.USE_SOURCE, null);
    }

    public static TimelineResolutionIntent useTarget(EntityRef entityRef, TimelineConflictType type) {
        return new TimelineResolutionIntent(entityRef, type, ResolutionMode.USE_TARGET, null);
    }
}
