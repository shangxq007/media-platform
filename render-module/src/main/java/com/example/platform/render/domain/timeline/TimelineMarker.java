package com.example.platform.render.domain.timeline;

/**
 * Timeline marker (maps to OTIO Marker).
 */
public record TimelineMarker(
        String id,
        String name,
        double timeSeconds,
        String color,
        String comment) {

    public static TimelineMarker of(String id, String name, double timeSeconds) {
        return new TimelineMarker(id, name, timeSeconds, null, null);
    }
}
