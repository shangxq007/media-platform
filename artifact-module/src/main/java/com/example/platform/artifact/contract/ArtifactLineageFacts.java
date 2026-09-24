package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
import java.util.Objects;

public record ArtifactLineageFacts(List<ArtifactId> parents, String operationId, int operationVersion) {
    public ArtifactLineageFacts {
        parents = parents == null ? List.of() : List.copyOf(parents);
        parents.forEach(p -> Objects.requireNonNull(p, "parent artifact"));
        if (operationId == null || operationId.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "operationId is required");
        if (operationVersion < 1) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "operationVersion is required");
    }
}
