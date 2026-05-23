package com.example.platform.render.domain.timeline;

import java.util.List;
import java.util.Map;

/**
 * A single effect instance applied to a timeline clip (editor / OTIO shape).
 */
public record TimelineClipEffect(
        String id,
        String effectKey,
        String packId,
        String packVersion,
        List<String> providerPreference,
        Map<String, Object> parameters) {

    public static TimelineClipEffect ofKey(String effectKey, Map<String, Object> parameters) {
        return new TimelineClipEffect(null, effectKey, null, null, List.of(), parameters != null ? parameters : Map.of());
    }
}
