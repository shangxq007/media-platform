package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;

/** Sole port for future upload admission; no feature module may issue Artifact identity itself. */
public interface ArtifactUploadAdmissionPort {
    PreparedSource prepare(ArtifactUploadAdmission admission);
    AdmissionResult admitPrepared(PreparedSource prepared, ArtifactUploadAdmission admission);

    /** Opaque implementation-owned proof that media validation completed. */
    interface PreparedSource {}

    record AdmissionResult(ArtifactId artifactId, String fingerprint) {
        public AdmissionResult {
            if (artifactId == null || fingerprint == null || fingerprint.isBlank()) throw new ArtifactContractException(ArtifactContractErrorCode.MISSING_REQUIRED_FACT, "admission result is incomplete");
        }
    }
}
