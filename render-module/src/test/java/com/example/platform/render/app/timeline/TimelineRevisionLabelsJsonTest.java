package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineRevisionLabelsJsonTest {

    @Test
    void normalizesAndDedupesLabels() {
        List<String> normalized = TimelineRevisionLabelsJson.normalize(
                List.of("  ai ", "ai", "review", "", "x".repeat(40)));
        assertEquals(3, normalized.size());
        assertTrue(normalized.contains("ai"));
        assertTrue(normalized.contains("review"));
        assertTrue(
                normalized.stream().allMatch(l -> l.length() <= TimelineRevisionLabelsJson.MAX_LABEL_LENGTH));
    }

    @Test
    void roundTripsJson() {
        String json = TimelineRevisionLabelsJson.toJson(List.of("tag-a", "tag-b"));
        assertTrue(json != null && json.contains("tag-a"));
        assertEquals(List.of("tag-a", "tag-b"), TimelineRevisionLabelsJson.parse(json));
    }
}
