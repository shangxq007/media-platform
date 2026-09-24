package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import java.util.Map;

public record ArtifactTechnicalMetadata(ArtifactKind kind, ArtifactMediaType mediaType, Map<String, Object> fields) {
    public ArtifactTechnicalMetadata {
        if (kind == null || mediaType == null) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "kind and mediaType are required");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
