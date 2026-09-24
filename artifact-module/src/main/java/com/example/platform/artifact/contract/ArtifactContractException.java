package com.example.platform.artifact.contract;

import java.util.Objects;

/** A fail-closed, machine-readable contract violation. */
public final class ArtifactContractException extends IllegalArgumentException {
    private final ArtifactContractErrorCode code;

    public ArtifactContractException(ArtifactContractErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ArtifactContractErrorCode code() { return code; }
}
