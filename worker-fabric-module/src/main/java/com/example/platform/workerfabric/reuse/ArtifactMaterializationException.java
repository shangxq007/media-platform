package com.example.platform.workerfabric.reuse;

import java.io.IOException;

/** Checked failure to obtain exact immutable Artifact bytes. */
public final class ArtifactMaterializationException extends IOException {
    public ArtifactMaterializationException(String message) {
        super(message);
    }

    public ArtifactMaterializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
