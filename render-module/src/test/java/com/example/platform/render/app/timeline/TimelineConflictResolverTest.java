package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent.ResolutionMode;
import java.util.*;
import org.junit.jupiter.api.Test;

class TimelineConflictResolverTest {

    private final TimelineConflictResolver resolver = new TimelineConflictResolver();

    @Test
    void shouldResolveConflictWithUseSource() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_1");
        SemanticChange sourceChange = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "trim source");
        SemanticChange targetChange = SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clip, "effect target");

        TimelineConflict conflict = TimelineConflict.of(clip, TimelineConflictType.SAME_ENTITY_MODIFIED,
                sourceChange, targetChange, "both sides modified");

        TimelineResolutionIntent intent = TimelineResolutionIntent.useSource(clip, TimelineConflictType.SAME_ENTITY_MODIFIED);

        SemanticChange resolved = resolver.resolve(conflict, intent);

        assertNotNull(resolved);
        assertEquals(SemanticChangeType.CLIP_RANGE_CHANGED, resolved.type());
        assertEquals("trim source", resolved.summary());
    }

    @Test
    void shouldResolveConflictWithUseTarget() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_2");
        SemanticChange sourceChange = SemanticChange.of(SemanticChangeType.CLIP_REMOVED, clip, "removed");
        SemanticChange targetChange = SemanticChange.of(SemanticChangeType.CLIP_SPEED_CHANGED, clip, "speed change");

        TimelineConflict conflict = TimelineConflict.of(clip, TimelineConflictType.CLIP_REMOVED_AND_MODIFIED,
                sourceChange, targetChange, "removed vs modified");

        TimelineResolutionIntent intent = TimelineResolutionIntent.useTarget(clip, TimelineConflictType.CLIP_REMOVED_AND_MODIFIED);

        SemanticChange resolved = resolver.resolve(conflict, intent);

        assertNotNull(resolved);
        assertEquals(SemanticChangeType.CLIP_SPEED_CHANGED, resolved.type());
    }

    @Test
    void shouldRejectMismatchedEntities() {
        EntityRef clipA = new EntityRef(EntityKind.CLIP, "clip_a");
        EntityRef clipB = new EntityRef(EntityKind.CLIP, "clip_b");
        SemanticChange change = SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clipA, "trim");

        TimelineConflict conflict = TimelineConflict.of(clipA, TimelineConflictType.SAME_ENTITY_MODIFIED,
                change, change, "same");
        TimelineResolutionIntent intent = TimelineResolutionIntent.useSource(clipB, TimelineConflictType.SAME_ENTITY_MODIFIED);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(conflict, intent));
    }

    @Test
    void shouldResolveMultipleConflicts() {
        EntityRef clip1 = new EntityRef(EntityKind.CLIP, "clip_1");
        EntityRef clip2 = new EntityRef(EntityKind.CLIP, "clip_2");

        TimelineConflict conflict1 = TimelineConflict.of(clip1, TimelineConflictType.SAME_ENTITY_MODIFIED,
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip1, "source trim"),
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip1, "target trim"),
                "same entity");
        TimelineConflict conflict2 = TimelineConflict.of(clip2, TimelineConflictType.EFFECT_CONFLICT,
                SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clip2, "source blur"),
                SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, clip2, "target sharpen"),
                "effect conflict");

        Map<String, TimelineResolutionIntent> intents = Map.of(
                clip1.key(), TimelineResolutionIntent.useSource(clip1, TimelineConflictType.SAME_ENTITY_MODIFIED),
                clip2.key(), TimelineResolutionIntent.useTarget(clip2, TimelineConflictType.EFFECT_CONFLICT));

        List<SemanticChange> resolved = resolver.resolveAll(List.of(conflict1, conflict2), intents);

        assertEquals(2, resolved.size());
        assertEquals(SemanticChangeType.CLIP_RANGE_CHANGED, resolved.get(0).type());
        assertEquals(SemanticChangeType.CLIP_EFFECT_CHANGED, resolved.get(1).type());
    }

    @Test
    void shouldDetectUnresolvedConflicts() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_1");
        TimelineConflict conflict = TimelineConflict.of(clip, TimelineConflictType.SAME_ENTITY_MODIFIED,
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "source"),
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "target"),
                "conflict");

        boolean allResolved = resolver.areAllResolved(List.of(conflict), Map.of());

        assertFalse(allResolved);
    }

    @Test
    void shouldConfirmAllResolvedWhenIntentsCoverAllConflicts() {
        EntityRef clip = new EntityRef(EntityKind.CLIP, "clip_1");
        TimelineConflict conflict = TimelineConflict.of(clip, TimelineConflictType.SAME_ENTITY_MODIFIED,
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "source"),
                SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, clip, "target"),
                "conflict");

        Map<String, TimelineResolutionIntent> intents = Map.of(
                clip.key(), TimelineResolutionIntent.useSource(clip, TimelineConflictType.SAME_ENTITY_MODIFIED));

        assertTrue(resolver.areAllResolved(List.of(conflict), intents));
    }
}
