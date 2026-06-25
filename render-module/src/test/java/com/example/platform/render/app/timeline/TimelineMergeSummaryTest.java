package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.internal.TimelineMergeSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineMergeSummaryTest {

    @Test
    void emptySummaryShouldHaveZeros() {
        TimelineMergeSummary summary = TimelineMergeSummary.empty();

        assertEquals(0, summary.mergedEntities());
        assertEquals(0, summary.autoMergedCount());
        assertEquals(0, summary.conflictCount());
        assertEquals(0, summary.sourceChangesApplied());
        assertEquals(0, summary.targetChangesApplied());
        assertTrue(summary.mergedEntityIds().isEmpty());
        assertTrue(summary.conflictedEntityIds().isEmpty());
    }

    @Test
    void mergedSummaryShouldTrackAppliedChanges() {
        List<String> mergedIds = List.of("CLIP:clip_a", "CLIP:clip_b");
        TimelineMergeSummary summary = TimelineMergeSummary.merged(3, 1, mergedIds);

        assertEquals(4, summary.autoMergedCount());
        assertEquals(3, summary.sourceChangesApplied());
        assertEquals(1, summary.targetChangesApplied());
        assertEquals(0, summary.conflictCount());
        assertEquals(2, summary.mergedEntityIds().size());
    }

    @Test
    void conflictSummaryShouldTrackBoth() {
        List<String> mergedIds = List.of("CLIP:clip_a");
        List<String> conflictIds = List.of("CLIP:clip_shared");
        TimelineMergeSummary summary = TimelineMergeSummary.conflicts(2, 1, mergedIds, conflictIds);

        assertEquals(3, summary.autoMergedCount());
        assertEquals(1, summary.conflictCount());
        assertEquals(1, summary.mergedEntityIds().size());
        assertEquals(1, summary.conflictedEntityIds().size());
        assertTrue(summary.conflictedEntityIds().contains("CLIP:clip_shared"));
    }

    @Test
    void summaryShouldTrackRejectedChanges() {
        // Edge case: some changes rejected
        List<String> merged = List.of("CLIP:clip_a");
        List<String> conflicted = List.of("CLIP:clip_b", "CLIP:clip_c");
        TimelineMergeSummary summary = new TimelineMergeSummary(
                1, 1, 2, 2, 1, 1, 0, merged, conflicted);

        assertEquals(2, summary.conflictCount());
        assertEquals(1, summary.sourceChangesRejected());
        assertEquals(2, summary.sourceChangesApplied());
    }
}
