package com.example.platform.artifact.contract;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable fingerprints for versioned contracts. Object keys are sorted recursively by canonicalizer. */
public final class ArtifactContractFingerprint {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ArtifactContractFingerprint() {}

    public static String upload(ArtifactUploadAdmission value) { return fingerprint(Map.of("version", value.contractVersion(), "scope", value.scope().canonicalForm(), "digest", value.digest().toString(), "size", value.sizeBytes(), "idempotency", value.idempotencyKey())); }
    public static String subject(ArtifactSubject value) { return fingerprint(Map.of("version", value.contractVersion(), "artifactId", value.artifactId().value(), "scope", value.scope().canonicalForm(), "visibility", value.visibility().name(), "owner", value.ownerId(), "lineage", value.lineageFingerprint(), "lifecycle", value.lifecycleFingerprint())); }
    public static String source(ArtifactSourceReference value) { return fingerprint(Map.of("version", value.contractVersion(), "artifactId", value.artifactId().value(), "digest", value.sourceDigest().toString(), "scope", value.scope().canonicalForm(), "lineage", value.lineageFingerprint(), "revision", value.artifactRevision())); }
    private static String fingerprint(Object value) {
        try { return ArtifactContractCanonicalizer.fingerprint(MAPPER.valueToTree(value)); }
        catch (IllegalArgumentException e) { throw new ArtifactContractException(ArtifactContractErrorCode.INVALID_FINGERPRINT, "contract fingerprint failed"); }
    }
}
