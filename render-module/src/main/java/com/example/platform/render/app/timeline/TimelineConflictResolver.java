package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent.ResolutionMode;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Resolves timeline merge conflicts based on user resolution intents.
 *
 * <p>Timeline-domain specific — NOT related to the infrastructure-level
 * {@code ConflictResolver} (which handles subsystem decisions).</p>
 *
 * <p>Phase 1 supports USE_SOURCE and USE_TARGET resolution modes.
 * MANUAL is reserved for future UI-driven resolution.</p>
 */
@Component
public class TimelineConflictResolver {

    /**
     * Apply a resolution intent to a single conflict, returning the resolved change.
     *
     * @param conflict the conflict to resolve
     * @param intent   user's resolution intent
     * @return the SemanticChange that should be applied (from source or target)
     */
    public SemanticChange resolve(TimelineConflict conflict, TimelineResolutionIntent intent) {
        if (!conflict.entityRef().equals(intent.entityRef())) {
            throw new IllegalArgumentException(
                    "Intent entity " + intent.entityRef().key() + " does not match conflict entity "
                            + conflict.entityRef().key());
        }

        return switch (intent.resolutionMode()) {
            case USE_SOURCE -> conflict.sourceChange();
            case USE_TARGET -> conflict.targetChange();
            case MANUAL -> throw new UnsupportedOperationException(
                    "MANUAL resolution not yet supported for " + conflict.entityRef().key());
        };
    }

    /**
     * Resolve a set of conflicts using a map of resolution intents.
     *
     * @param conflicts list of conflicts detected during merge
     * @param intents   resolution intents keyed by entity
     * @return list of resolved SemanticChanges (from the chosen side for each conflict)
     * @throws IllegalArgumentException if any conflict has no matching intent
     */
    public List<SemanticChange> resolveAll(List<TimelineConflict> conflicts,
                                             Map<String, TimelineResolutionIntent> intents) {
        List<SemanticChange> resolved = new ArrayList<>();
        Set<String> unresolved = new LinkedHashSet<>();

        for (TimelineConflict conflict : conflicts) {
            TimelineResolutionIntent intent = intents.get(conflict.entityRef().key());
            if (intent == null) {
                unresolved.add(conflict.entityRef().key());
                continue;
            }
            resolved.add(resolve(conflict, intent));
        }

        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing resolution intents for conflicts: " + String.join(", ", unresolved));
        }

        return resolved;
    }

    /**
     * Check whether all conflicts have been resolved with valid intents.
     */
    public boolean areAllResolved(List<TimelineConflict> conflicts,
                                   Map<String, TimelineResolutionIntent> intents) {
        return conflicts.stream()
                .allMatch(c -> intents.containsKey(c.entityRef().key()));
    }
}
