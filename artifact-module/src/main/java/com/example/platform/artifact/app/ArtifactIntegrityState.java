package com.example.platform.artifact.app;

/**
 * Redacted integrity projection. DIGEST_RECORDED does not claim that storage
 * bytes were re-scanned; it only states that canonical immutable digest truth
 * exists. Quarantine/failure remain visible without exposing scanner details.
 */
public enum ArtifactIntegrityState {
    DIGEST_RECORDED,
    QUARANTINED,
    FAILED,
    UNAVAILABLE
}
