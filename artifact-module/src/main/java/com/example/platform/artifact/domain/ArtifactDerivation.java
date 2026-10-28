package com.example.platform.artifact.domain;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import java.util.Objects;

/** Declared provenance intent; the result digest is supplied only after real output validation. */
public record ArtifactDerivation(ArtifactId parentArtifactId, ProvenanceRelationType relationType,
        String operationId, int operationVersion, ContentDigest requestDigest) {
    public ArtifactDerivation {
        Objects.requireNonNull(parentArtifactId);Objects.requireNonNull(relationType);Objects.requireNonNull(requestDigest);
        if(operationId==null||operationId.isBlank()||operationVersion<1)throw new IllegalArgumentException("derivation operation required");
    }
    public ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration(String attemptId,ContentDigest resultDigest) {
        if(attemptId==null||attemptId.isBlank())throw new IllegalArgumentException("fenced attempt required");
        return new ArtifactCommitRequest.ProvenanceEdgeDeclaration(parentArtifactId,relationType,operationId,
                operationVersion,attemptId,requestDigest.canonicalValue(),Objects.requireNonNull(resultDigest).canonicalValue());
    }
}
