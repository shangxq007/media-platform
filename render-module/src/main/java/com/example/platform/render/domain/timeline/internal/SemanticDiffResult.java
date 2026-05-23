package com.example.platform.render.domain.timeline.internal;

import java.util.List;

public record SemanticDiffResult(
        String oldTimelineId,
        String newTimelineId,
        int oldRevision,
        int newRevision,
        String schemaVersion,
        List<SemanticChange> changes,
        boolean structurallyEqual) {

    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
