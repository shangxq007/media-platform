package com.example.platform.render.domain.timeline.semantics.selection;

import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import java.util.List;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SS10): deterministic
 * revision-bound scope resolution result.
 *
 * <p>Records the exact base revision (id + content hash where available) it was
 * resolved against, the ordered deduplicated clip id list, and the expansion
 * policy that was applied. NEVER resolved against mutable latest; old scopes
 * must not be reused for a different revision (stale => reject/re-resolve).
 */
public record ResolvedScope(
        String baseRevisionId,
        String baseContentHash,
        List<TimelineClipId> resolvedClipIds,
        SelectionSpec.ExpansionPolicy expansionPolicy) {

    public ResolvedScope {
        if (baseRevisionId == null || baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId required (revision-bound resolution)");
        }
        if (resolvedClipIds == null) {
            throw new IllegalArgumentException("resolvedClipIds required");
        }
        if (expansionPolicy == null) {
            throw new IllegalArgumentException("expansionPolicy required");
        }
    }
}
