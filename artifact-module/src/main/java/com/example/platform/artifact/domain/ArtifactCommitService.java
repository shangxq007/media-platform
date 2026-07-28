package com.example.platform.artifact.domain;

import java.util.List;
import java.util.Optional;

/**
 * Transactional service for atomically creating Artifacts with their replica bindings
 * and provenance edges.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Atomic creation: Artifact, ArtifactReplicaBinding, ProvenanceEdges, Idempotency record</li>
 *   <li>Database transaction failure → no partial Artifact Metadata</li>
 *   <li>Storage object already committed but DB fails → don't silently delete;
 *       return stable error with storageObjectId, storageReplicaId, idempotencyKey for reconciliation</li>
 *   <li>Same tenant + idempotencyKey + canonical commit request → same Artifact result</li>
 *   <li>Same idempotency key with different request → ARTIFACT_IDEMPOTENCY_CONFLICT</li>
 *   <li>Concurrent same-idempotency requests → exactly one logical commit</li>
 * </ul>
 */
public interface ArtifactCommitService {

    /**
     * Atomically commits a new Artifact.
     *
     * @param request the commit request
     * @return the commit result
     * @throws ArtifactErrorCode.ArtifactDomainException on domain errors
     * @throws ArtifactErrorCode.ProvenanceException on provenance violations
     */
    ArtifactCommitResult commit(ArtifactCommitRequest request);

    /**
     * Returns the result of a previous idempotent commit, if one exists.
     *
     * @param tenantId the tenant
     * @param idempotencyKey the idempotency key
     * @return the previous result, or empty if not found
     */
    Optional<ArtifactCommitResult> findByIdempotencyKey(String tenantId, String idempotencyKey);
}
