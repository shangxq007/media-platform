package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.TimelineConflict;
import com.example.platform.render.domain.timeline.internal.TimelineConflictType;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineConflictDetectorTest {

    private final TimelineConflictDetector detector = new TimelineConflictDetector();

    @Test
    void differentEntitiesShouldNotConflict() {
        EntityRef clipA = new EntityRef(EntityKind.CLIP, "clip_1");
        EntityRef clipB = new EntityRef(EntityKind.CLIP, "clip_2");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clipA, "trim clip A"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clipB, "trim clip B"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void sameClipRangeModifiedOnBothSidesShouldConflict() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_shared");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim from 10s to 8s"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim from 10s to 12s"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertEquals(1, conflicts.size());
        assertEquals(TimelineConflictType.SAME_ENTITY_MODIFIED, conflicts.get(0).conflictType());
    }

    @Test
    void clipRemovedOnOneSideAndModifiedOnOtherShouldConflict() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_doomed");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_REMOVED, clip, "removed clip"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_SPEED_CHANGED, clip, "changed speed"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertEquals(1, conflicts.size());
        assertEquals(TimelineConflictType.CLIP_REMOVED_AND_MODIFIED, conflicts.get(0).conflictType());
    }

    @Test
    void sameMetadataChangedDifferentlyShouldConflict() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_meta");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_METADATA_CHANGED, clip, "changed label"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_METADATA_CHANGED, clip, "changed label differently"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertEquals(1, conflicts.size());
        assertEquals(TimelineConflictType.SAME_ENTITY_MODIFIED, conflicts.get(0).conflictType());
    }

    @Test
    void revisionOnlyChangesShouldNotConflict() {
        EntityRef proj = new EntityRef(EntityKind.PROJECT, "proj_1");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.REVISION_ONLY, proj, "revision bump"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.REVISION_ONLY, proj, "revision bump"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void emptyChangesShouldProduceNoConflicts() {
        List<TimelineConflict> conflicts = detector.detect(List.of(), List.of());
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void effectChangedOnBothSidesShouldConflict() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_eff");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clip, "added blur"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clip, "added sharpen"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertEquals(1, conflicts.size());
        assertEquals(TimelineConflictType.SAME_ENTITY_MODIFIED, conflicts.get(0).conflictType());
    }

    @Test
    void oneSideOnlyShouldNotConflict() {
        EntityRef clipA = new EntityRef(EntityKind.CLIP, "clip_a");
        EntityRef clipB = new EntityRef(EntityKind.CLIP, "clip_b");

        List<SemanticChange> source = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clipA, "trim"));
        List<SemanticChange> target = List.of(
                SemanticChange.of(SemanticChangeType.CLIP_ADDED, clipB, "add new clip"));

        List<TimelineConflict> conflicts = detector.detect(source, target);

        assertTrue(conflicts.isEmpty());
    }
}
