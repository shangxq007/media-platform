package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactKind;
import java.util.Objects;

public record ArtifactSearchQuery(ArtifactScope scope, String filter, ArtifactKind kind, int limit, String cursor) {
    public ArtifactSearchQuery {
        Objects.requireNonNull(scope, "scope");
        if (limit < 1 || limit > 200) throw new ArtifactContractException(ArtifactContractErrorCode.INVALID_CURSOR, "limit must be 1..200");
        if (cursor != null && cursor.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.INVALID_CURSOR, "cursor must not be blank");
        filter = filter == null ? "" : filter;
    }
}
