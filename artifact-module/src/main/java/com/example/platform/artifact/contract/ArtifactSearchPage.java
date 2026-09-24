package com.example.platform.artifact.contract;

import java.util.List;

public record ArtifactSearchPage(List<ArtifactProjection> items, String nextCursor, Consistency consistency) {
    public ArtifactSearchPage {
        items = items == null ? List.of() : List.copyOf(items);
        consistency = consistency == null ? Consistency.CONSISTENT : consistency;
        if (nextCursor != null && nextCursor.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.INVALID_CURSOR, "nextCursor must not be blank");
    }
    public enum Consistency { CONSISTENT, REBUILDING, STALE, UNAVAILABLE }
}
