package com.example.platform.artifact.contract;

/** Stable failure codes shared by Artifact-native feature boundaries. */
public enum ArtifactContractErrorCode {
    MISSING_REQUIRED_FACT,
    CONFLICTING_FACT,
    DUPLICATE_IDEMPOTENCY_KEY,
    SCOPE_MISMATCH,
    ARTIFACT_NOT_RETRIEVABLE,
    INTEGRITY_VERIFICATION_FAILED,
    INVALID_CURSOR,
    STALE_INDEX,
    INVALID_FINGERPRINT
}
