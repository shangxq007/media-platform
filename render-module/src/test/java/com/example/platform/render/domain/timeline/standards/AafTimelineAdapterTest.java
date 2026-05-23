package com.example.platform.render.domain.timeline.standards;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineSpec;
import org.junit.jupiter.api.Test;

class AafTimelineAdapterTest {

    @Test
    void parsesJsonManifest() {
        String json = """
                {
                  "id": "aaf-demo",
                  "slots": [
                    {"id":"s1","mediaUri":"file:///tmp/a.mp4","duration":4,"timelineStart":0},
                    {"id":"s2","mediaUri":"file:///tmp/b.mp4","duration":3,"timelineStart":4}
                  ]
                }
                """;
        TimelineSpec spec = AafTimelineAdapter.parseJsonManifest(json, null);
        assertEquals("aaf-demo", spec.id());
        assertEquals(2, spec.tracks().get(0).clips().size());
        assertEquals("MANIFEST_JSON", spec.metadata().get("platform.import.status"));
        assertTrue(spec.computeDuration() >= 7);
    }

    @Test
    void placeholderWhenNoManifest() {
        TimelineSpec spec = AafTimelineAdapter.importFromSource("/data/edit.aaf", null, "file:///fallback.mp4");
        assertEquals("PLACEHOLDER_REQUIRES_WORKER", spec.metadata().get("platform.import.status"));
    }
}
