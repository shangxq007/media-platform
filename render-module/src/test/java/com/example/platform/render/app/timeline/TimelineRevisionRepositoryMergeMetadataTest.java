package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TimelineRevisionRepositoryMergeMetadataTest {

    @Test
    void revisionRowShouldSupportMergeFields() {
        var row = new TimelineRevisionRepository.RevisionRow(
                "trev_001", "proj_1", "tenant_1",
                "trev_parent", 5, "snap_001", 0,
                "hash_abc", "internal-1.0", "merge",
                "user_1", "session_1", "Merge branches",
                null, null, null,
                true,
                "trev_source,trev_target",
                "trev_base",
                java.time.OffsetDateTime.now());

        assertTrue(row.isMerge());
        assertEquals("trev_source,trev_target", row.mergeParentRevisionIds());
        assertEquals("trev_base", row.mergeBaseRevisionId());
        assertEquals("merge", row.source());
    }

    @Test
    void nonMergeRevisionShouldHaveDefaults() {
        var row = new TimelineRevisionRepository.RevisionRow(
                "trev_002", "proj_1", "tenant_1",
                "trev_parent", 6, "snap_002", 0,
                "hash_def", "internal-1.0", "sync",
                null, null, "Normal edit",
                null, null, null,
                false, null, null,
                java.time.OffsetDateTime.now());

        assertFalse(row.isMerge());
        assertNull(row.mergeParentRevisionIds());
        assertNull(row.mergeBaseRevisionId());
    }
}
