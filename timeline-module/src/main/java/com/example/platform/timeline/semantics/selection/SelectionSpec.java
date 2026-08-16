package com.example.platform.timeline.semantics.selection;

import com.example.platform.timeline.canonical.TimelineClipId;
import java.util.List;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SS1/SS2): selection is
 * APPLICATION/REQUEST state — NEVER canonical Timeline state. Creating or
 * changing a SelectionSpec must not alter Timeline serialization/hash/revision.
 *
 * <p>V1 variants: {@link ExplicitObjectSelection} and
 * {@link TimelineTimeRangeSelection}. No SQL/Cypher/JSONPath/scripting.
 */
public sealed interface SelectionSpec permits
        SelectionSpec.ExplicitObjectSelection,
        SelectionSpec.TimelineTimeRangeSelection {

    /** Explicit V1 expansion policy (SS13): never implicit global expansion. */
    enum ExpansionPolicy {
        EXACT,
        EXPAND_GROUP,
        EXPAND_SYNC
    }

    record ExplicitObjectSelection(List<TimelineClipId> clipIds) implements SelectionSpec {
        public ExplicitObjectSelection {
            if (clipIds == null || clipIds.isEmpty()) {
                throw new IllegalArgumentException("explicit selection must not be empty");
            }
        }
    }

    /** TIMELINE TIME range selection (never ambiguous source time); INTERSECTS overlap. */
    record TimelineTimeRangeSelection(com.example.platform.shared.time.MediaTime start,
                                      com.example.platform.shared.time.MediaTime end)
            implements SelectionSpec {
        public TimelineTimeRangeSelection {
            if (start == null || end == null) {
                throw new IllegalArgumentException("time range selection requires exact start/end");
            }
            if (start.isGreaterThan(end)) {
                throw new IllegalArgumentException("time range selection start must be <= end");
            }
        }
    }
}
