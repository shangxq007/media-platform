package com.example.platform.artifact.app;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
/** Artifact-owned acceptance of a verified Storage output. */
public interface ArtifactOutputCommit {
    ArtifactOutputReference commit(ArtifactScope scope, com.example.platform.storage.api.StorageOutputPort.WrittenOutput output);
}
