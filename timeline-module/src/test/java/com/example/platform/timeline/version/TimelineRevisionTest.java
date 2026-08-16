package com.example.platform.timeline.version;

import com.example.platform.timeline.canonical.TimelineDocument;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionTest {

    @Test
    void rootRevision_hasNullParent() {
        var revision = new TimelineRevision(
                "rev-1", "prod-1", null, "timeline-1.0",
                null, "digest-123", Instant.now(), "user-1");

        assertTrue(revision.isRoot());
        assertNull(revision.parentRevisionId());
    }

    @Test
    void childRevision_hasParent() {
        var revision = new TimelineRevision(
                "rev-2", "prod-1", "rev-1", "timeline-1.0",
                null, "digest-456", Instant.now(), "user-1");

        assertFalse(revision.isRoot());
        assertEquals("rev-1", revision.parentRevisionId());
    }

    @Test
    void nullRevisionId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRevision(null, "prod-1", null, "timeline-1.0",
                        null, "digest", Instant.now(), "user-1"));
    }

    @Test
    void nullProductId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRevision("rev-1", null, null, "timeline-1.0",
                        null, "digest", Instant.now(), "user-1"));
    }

    @Test
    void nullDigest_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineRevision("rev-1", "prod-1", null, "timeline-1.0",
                        null, null, Instant.now(), "user-1"));
    }

    @Test
    void withContent_createsNewRevision() {
        var original = new TimelineRevision(
                "rev-1", "prod-1", null, "timeline-1.0",
                null, "digest-old", Instant.now(), "user-1");

        var updated = original.withContent(null, "digest-new");

        assertEquals("rev-1", updated.revisionId());
        assertEquals("digest-new", updated.contentDigest());
    }

    @Test
    void schemaVersion_preserved() {
        var revision = new TimelineRevision(
                "rev-1", "prod-1", null, "timeline-1.0",
                null, "digest", Instant.now(), "user-1");

        assertEquals("timeline-1.0", revision.timelineSchemaVersion());
    }
}
