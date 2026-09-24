package com.example.platform.artifact.contract;

import com.example.platform.shared.digest.ContentDigest;
import java.util.Objects;

/** Versioned admission facts. Implementations must commit through the Artifact authority. */
public record ArtifactUploadAdmission(
        int contractVersion,
        ArtifactScope scope,
        ArtifactStorageReference storage,
        ContentDigest digest,
        long sizeBytes,
        ArtifactTechnicalMetadata technicalMetadata,
        ArtifactAuditFacts audit,
        ArtifactLifecycleFacts lifecycle,
        ArtifactLineageFacts lineage,
        String idempotencyKey,
        String quotaDecisionId
) {
    public static final int CURRENT_CONTRACT_VERSION = 1;
    public ArtifactUploadAdmission {
        if (contractVersion != CURRENT_CONTRACT_VERSION) throw new ArtifactContractException(ArtifactContractErrorCode.CONFLICTING_FACT, "unsupported upload contract version");
        Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(storage, "storage"); Objects.requireNonNull(digest, "digest");
        if (sizeBytes < 0) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "sizeBytes is required");
        Objects.requireNonNull(technicalMetadata, "technicalMetadata"); Objects.requireNonNull(audit, "audit"); Objects.requireNonNull(lifecycle, "lifecycle"); Objects.requireNonNull(lineage, "lineage");
        requireText(idempotencyKey, "idempotencyKey"); requireText(quotaDecisionId, "quotaDecisionId");
    }
    private static void requireText(String v, String n) { if (v == null || v.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, n + " is required"); }
}
