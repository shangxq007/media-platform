package com.example.platform.timeline.diff.merge;

import java.util.Map;

public record SemanticChange(
        SemanticChangeType type,
        EntityRef entity,
        String summary,
        Map<String, String> details) {

    public static SemanticChange of(SemanticChangeType type, EntityRef entity, String summary) {
        return new SemanticChange(type, entity, summary, Map.of());
    }
}
