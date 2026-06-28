package com.example.platform.render.domain.timeline.diff;

import java.util.Map;

/**
 * Safe payload for a timeline change — before/after values.
 * Internal domain model. No binary data, no storage references, no commands.
 */
public record TimelineChangePayload(
        String stringValue,
        Map<String, String> objectValue) {

    public static TimelineChangePayload ofString(String v) {
        return new TimelineChangePayload(v, null);
    }

    public static TimelineChangePayload ofMap(Map<String, String> v) {
        return new TimelineChangePayload(null, v);
    }

    public static TimelineChangePayload empty() {
        return new TimelineChangePayload(null, null);
    }
}
