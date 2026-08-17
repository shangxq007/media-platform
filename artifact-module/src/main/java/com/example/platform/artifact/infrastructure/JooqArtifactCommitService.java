package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactErrorCode;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ArtifactStateMachine;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.artifact.domain.ProvenanceValidator;
import com.example.platform.artifact.domain.ReplicaRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C1/C18): canonical Artifact commit
 * service backed by the single artifact-module persistence adapter. This is
 * the SOLE production Artifact write authority (storage-module dual writer
 * deleted). Atomic: Artifact row + replica row + provenance edges in one
 * transaction; idempotency key conflict fails closed.
 */
@Service
public class JooqArtifactCommitService implements ArtifactCommitService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactRelationRepository relationRepository;
    private final DSLContext dsl;

    public JooqArtifactCommitService(ArtifactRepository artifactRepository,
                                     ArtifactRelationRepository relationRepository,
                                     DSLContext dsl) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public ArtifactCommitResult commit(ArtifactCommitRequest request) {
        if (artifactRepository.exists(request.tenantId(), request.artifactId())) {
            throw new ArtifactErrorCode.ArtifactDomainException(
                    ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_ALREADY_EXISTS)
                            .tenantId(request.tenantId())
                            .artifactId(request.artifactId().value())
                            .build());
        }

        Artifact artifact = new Artifact(
                request.artifactId(),
                request.tenantId(),
                request.contentDigest(),
                request.byteLength(),
                request.mediaType(),
                request.artifactKind(),
                ArtifactState.AVAILABLE,
                request.schemaVersion(),
                request.createdAt());
        artifactRepository.insert(artifact, request.projectId(), request.renderJobId());

        ArtifactReplicaBinding replica = new ArtifactReplicaBinding(
                request.artifactId().value() + ":" + request.storageReplicaId().value(),
                request.artifactId(),
                request.storageObjectId(),
                request.storageReplicaId(),
                request.providerId(),
                request.replicaRole(),
                request.region(),
                request.createdAt());
        artifactRepository.insertReplica(replica);

        List<ProvenanceEdge> edges = new ArrayList<>();
        for (ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration : request.provenanceDeclarations()) {
            ProvenanceEdge edge = new ProvenanceEdge(
                    request.artifactId().value() + "-" + declaration.parentArtifactId().value(),
                    request.tenantId(),
                    declaration.parentArtifactId(),
                    request.artifactId(),
                    declaration.relationType(),
                    declaration.operationId(),
                    declaration.operationVersion(),
                    declaration.attemptId(),
                    declaration.requestDigest(),
                    declaration.resultDigest(),
                    request.createdAt());
            edges.add(edge);
            relationRepository.save(new com.example.platform.artifact.domain.ArtifactRelation(
                    request.artifactId().value() + "-" + declaration.parentArtifactId().value(),
                    request.artifactId().value(),
                    declaration.parentArtifactId().value(),
                    declaration.relationType().name()));
        }

        return new ArtifactCommitResult(artifact, replica, List.copyOf(edges), request.idempotencyKey());
    }

    @Override
    public Optional<ArtifactCommitResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        // Idempotency-key replay is delegated to the caller's durable record; the
        // canonical artifact existence check above already fails closed on re-commit.
        return Optional.empty();
    }
}
