package com.example.platform.artifact.contract;

import java.time.Instant;
import java.util.Objects;

public record ArtifactAuditFacts(String actorId, String requestId, Instant recordedAt) {
    public ArtifactAuditFacts {
        requireText(actorId, "actorId"); requireText(requestId, "requestId");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
    private static void requireText(String v, String n) { if (v == null || v.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, n + " is required"); }
}
