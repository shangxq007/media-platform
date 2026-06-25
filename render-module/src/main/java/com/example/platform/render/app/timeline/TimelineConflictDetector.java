package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.TimelineConflict;
import com.example.platform.render.domain.timeline.internal.TimelineConflictType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Detects conflicts between two sets of semantic changes during a three-way merge.
 *
 * <p>Conservative approach: when in doubt, report a conflict rather than silently merging.
 * Different entities = no conflict. Same entity modified on both sides = potential conflict.</p>
 */
@Component
public class TimelineConflictDetector {

    /**
     * Detect conflicts between changes from source and target branches.
     *
     * @param sourceChanges changes from base → source branch
     * @param targetChanges changes from base → target branch
     * @return list of conflicts (empty if none detected)
     */
    public List<TimelineConflict> detect(List<SemanticChange> sourceChanges,
                                          List<SemanticChange> targetChanges) {
        Map<String, List<SemanticChange>> sourceByEntity = groupByEntity(sourceChanges);
        Map<String, List<SemanticChange>> targetByEntity = groupByEntity(targetChanges);
        List<TimelineConflict> conflicts = new ArrayList<>();

        for (var entry : sourceByEntity.entrySet()) {
            String key = entry.getKey();
            EntityRef ref = entry.getValue().get(0).entity();
            List<SemanticChange> sourceList = entry.getValue();
            List<SemanticChange> targetList = targetByEntity.get(key);

            if (targetList == null || targetList.isEmpty()) {
                continue;
            }

            SemanticChange sourceChange = sourceList.get(0);
            SemanticChange targetChange = targetList.get(0);
            TimelineConflict conflict = classifyConflict(ref, sourceChange, targetChange);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    /**
     * Group semantic changes by entity reference key.
     */
    private Map<String, List<SemanticChange>> groupByEntity(List<SemanticChange> changes) {
        Map<String, List<SemanticChange>> grouped = new LinkedHashMap<>();
        for (SemanticChange change : changes) {
            if (change.entity() == null) {
                continue;
            }
            grouped.computeIfAbsent(change.entity().key(), k -> new ArrayList<>()).add(change);
        }
        return grouped;
    }

    /**
     * Classify whether changes to the same entity from two branches constitute a conflict.
     */
    private TimelineConflict classifyConflict(EntityRef ref, SemanticChange sourceChange,
                                               SemanticChange targetChange) {
        var sourceType = sourceChange.type();
        var targetType = targetChange.type();

        if (sourceType == targetType && sourceType.name().startsWith("REVISION")) {
            return null;
        }

        if (sourceType == targetType) {
            return TimelineConflict.of(ref, TimelineConflictType.SAME_ENTITY_MODIFIED,
                    sourceChange, targetChange,
                    "same entity modified on both branches: " + sourceType);
        }

        boolean sourceRemoved = sourceType.name().contains("REMOVED");
        boolean targetRemoved = targetType.name().contains("REMOVED");

        if (sourceRemoved && targetType.name().contains("CHANGED")) {
            return TimelineConflict.of(ref, TimelineConflictType.CLIP_REMOVED_AND_MODIFIED,
                    sourceChange, targetChange,
                    "removed in source, modified in target");
        }
        if (targetRemoved && sourceType.name().contains("CHANGED")) {
            return TimelineConflict.of(ref, TimelineConflictType.CLIP_REMOVED_AND_MODIFIED,
                    sourceChange, targetChange,
                    "modified in source, removed in target");
        }

        if (sourceType.name().contains("RANGE") || targetType.name().contains("RANGE")
                || sourceType.name().contains("MOVED") || targetType.name().contains("MOVED")) {
            return TimelineConflict.of(ref, TimelineConflictType.CLIP_RANGE_CONFLICT,
                    sourceChange, targetChange,
                    "clip time range modified on both branches");
        }

        if (sourceType.name().contains("EFFECT") || targetType.name().contains("EFFECT")) {
            return TimelineConflict.of(ref, TimelineConflictType.EFFECT_CONFLICT,
                    sourceChange, targetChange,
                    "effect modified on both branches");
        }

        if (sourceType.name().contains("METADATA") || targetType.name().contains("METADATA")) {
            return TimelineConflict.of(ref, TimelineConflictType.METADATA_CONFLICT,
                    sourceChange, targetChange,
                    "metadata modified on both branches");
        }

        return TimelineConflict.of(ref, TimelineConflictType.UNKNOWN,
                sourceChange, targetChange,
                "changes conflict: " + sourceType + " vs " + targetType);
    }
}
