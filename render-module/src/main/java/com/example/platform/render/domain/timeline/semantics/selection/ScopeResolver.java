package com.example.platform.render.domain.timeline.semantics.selection;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.render.domain.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.render.domain.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SS9/SS13/SS15): deterministic
 * revision-bound scope resolver at the Timeline/application boundary.
 *
 * <p>Resolves a SelectionSpec + ExpansionPolicy against ONE exact immutable
 * base Timeline revision (id + content hash recorded). Ordering: Timeline
 * placement order then TimelineClipId. Dedup by TimelineClipId. Bounded
 * single-hop expansion (EXACT / EXPAND_GROUP / EXPAND_SYNC); no unbounded
 * traversal; no mutable-latest fallback; no implicit global linked selection.
 */
public final class ScopeResolver {

    private ScopeResolver() {
    }

    public static ResolvedScope resolve(
            TimelineDocument baseRevision,
            String baseRevisionId,
            String baseContentHash,
            SelectionSpec spec,
            SelectionSpec.ExpansionPolicy policy) {
        Objects.requireNonNull(baseRevision, "baseRevision");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(policy, "policy");

        // 1. Timeline placement order of all clips (canonical document order)
        List<TimelineClipId> placementOrder = new ArrayList<>();
        for (var track : baseRevision.getTracks()) {
            for (TimelineClip clip : track.clips()) {
                placementOrder.add(clip.getClipId());
            }
        }

        // 2. Initial selection (EXPLICIT_OBJECTS or TIMELINE_TIME_RANGE)
        Set<TimelineClipId> resolved = new LinkedHashSet<>();
        if (spec instanceof SelectionSpec.ExplicitObjectSelection exp) {
            for (TimelineClipId id : exp.clipIds()) {
                if (!placementOrder.contains(id)) {
                    throw new IllegalArgumentException(
                            "selection references missing clip: " + id + " (fail closed)");
                }
                resolved.add(id);
            }
        } else if (spec instanceof SelectionSpec.TimelineTimeRangeSelection tr) {
            for (var track : baseRevision.getTracks()) {
                for (TimelineClip clip : track.clips()) {
                    if (overlaps(clip, tr.start(), tr.end())) {
                        resolved.add(clip.getClipId());
                    }
                }
            }
        }

        // 3. Explicit bounded expansion (never implicit)
        switch (policy) {
            case EXACT -> { /* no expansion */ }
            case EXPAND_GROUP -> expandGroup(baseRevision, resolved);
            case EXPAND_SYNC -> expandSync(baseRevision, resolved);
        }

        // 4. Deterministic ordering: placement order, then clip id
        List<TimelineClipId> ordered = resolved.stream()
                .sorted(Comparator
                        .comparingInt((TimelineClipId id) -> placementOrder.indexOf(id))
                        .thenComparing(TimelineClipId::value))
                .toList();

        return new ResolvedScope(baseRevisionId, baseContentHash, List.copyOf(ordered), policy);
    }

    /** TIMELINE TIME INTERSECTS overlap (exact MediaTime). */
    private static boolean overlaps(TimelineClip clip, MediaTime selStart, MediaTime selEnd) {
        MediaTime clipStart = clip.getStartTime();
        MediaTime clipEnd = clip.getEndTime();
        return clipStart.isLessThan(selEnd) && selStart.isLessThan(clipEnd);
    }

    /** Direct group membership expansion (flat groups, single hop). */
    private static void expandGroup(TimelineDocument doc, Set<TimelineClipId> resolved) {
        List<TimelineClipId> initial = List.copyOf(resolved);
        for (SemanticRelationship rel : doc.getSemanticRelationships()) {
            if (rel instanceof GroupRelationship group) {
                if (group.members().stream().anyMatch(initial::contains)) {
                    resolved.addAll(group.members());
                }
            }
        }
    }

    /** Bounded sync endpoint expansion (single hop, no continuous-lock semantics). */
    private static void expandSync(TimelineDocument doc, Set<TimelineClipId> resolved) {
        List<TimelineClipId> initial = List.copyOf(resolved);
        for (SemanticRelationship rel : doc.getSemanticRelationships()) {
            if (rel instanceof SyncRelationship sync) {
                if (initial.contains(sync.endpointA())) {
                    resolved.add(sync.endpointB());
                }
                if (initial.contains(sync.endpointB())) {
                    resolved.add(sync.endpointA());
                }
            }
        }
    }
}
