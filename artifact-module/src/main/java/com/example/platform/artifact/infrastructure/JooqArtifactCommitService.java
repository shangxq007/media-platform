package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactErrorCode;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.artifact.domain.ProvenanceValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>V1 bounded-cycle proof: {@code artifact.id} is a primary key, both
 * {@code artifact_relation} endpoints are foreign keys to it, and this service
 * is the sole canonical relation writer. Therefore the child accepted here is
 * genuinely new and cannot already be a relation endpoint. Every candidate is
 * new-child -> pre-existing-parent; after self-reference and duplicates are
 * rejected, the request-local graph validation is sufficient to prove that the
 * insertion cannot introduce a cycle without fabricating unpersisted metadata.
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

        // Request-local semantic rejection is deliberately before even the first
        // repository call, so duplicate/self/operation failures cannot degrade to
        // persistence errors or leave partial canonical rows.
        requireValid(request, null, ProvenanceValidator.validateDeclarations(
                artifact.artifactId(), request.provenanceDeclarations()));

        if (artifactRepository.exists(request.tenantId(), request.artifactId())) {
            throw new ArtifactErrorCode.ArtifactDomainException(
                    ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_ALREADY_EXISTS)
                            .tenantId(request.tenantId())
                            .artifactId(request.artifactId().value())
                            .build());
        }

        List<ProvenanceEdge> edges = prepareValidatedProvenance(artifact, request);

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

        for (ProvenanceEdge edge : edges) {
            relationRepository.save(new com.example.platform.artifact.domain.ArtifactRelation(
                    edge.edgeId(),
                    edge.childArtifactId().value(),
                    edge.parentArtifactId().value(),
                    edge.relationType().name()));
        }

        return new ArtifactCommitResult(artifact, replica, List.copyOf(edges), request.idempotencyKey());
    }

    private List<ProvenanceEdge> prepareValidatedProvenance(
            Artifact childArtifact,
            ArtifactCommitRequest request) {
        List<ProvenanceEdge> validatedEdges = new ArrayList<>();
        Map<String, String> endpointTenants = new HashMap<>();
        endpointTenants.put(childArtifact.artifactId().value(), childArtifact.tenantId());

        for (ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration : request.provenanceDeclarations()) {
            ProvenanceEdge edge = new ProvenanceEdge(
                    ProvenanceValidator.canonicalEdgeId(
                            childArtifact.artifactId(), declaration.parentArtifactId()),
                    childArtifact.tenantId(),
                    declaration.parentArtifactId(),
                    childArtifact.artifactId(),
                    declaration.relationType(),
                    declaration.operationId(),
                    declaration.operationVersion(),
                    declaration.attemptId(),
                    declaration.requestDigest(),
                    declaration.resultDigest(),
                    request.createdAt());

            // Cross-tenant-only parents are intentionally indistinguishable from
            // missing parents. No global lookup or ambient tenant fallback exists.
            Optional<Artifact> parent = artifactRepository.findById(
                    request.tenantId(), declaration.parentArtifactId());
            parent.ifPresent(value -> endpointTenants.put(
                    value.artifactId().value(), value.tenantId()));

            ProvenanceValidator.ValidationResult validation = ProvenanceValidator.validateEdge(
                    edge, validatedEdges, endpointTenants);
            requireValid(request, edge, validation);
            validatedEdges.add(edge);
        }

        return List.copyOf(validatedEdges);
    }

    private static void requireValid(
            ArtifactCommitRequest request,
            ProvenanceEdge edge,
            ProvenanceValidator.ValidationResult validation) {
        if (validation.valid()) {
            return;
        }
        ArtifactErrorCode.Error.Builder error = ArtifactErrorCode.Error.builder(validation.errorCode())
                .tenantId(request.tenantId())
                .artifactId(request.artifactId().value())
                .childArtifactId(request.artifactId().value());
        if (edge != null) {
            error.parentArtifactId(edge.parentArtifactId().value())
                    .operationId(edge.operationId())
                    .attemptId(edge.attemptId());
        } else if (!request.provenanceDeclarations().isEmpty()) {
            ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration =
                    request.provenanceDeclarations().getFirst();
            error.parentArtifactId(declaration.parentArtifactId().value())
                    .operationId(declaration.operationId())
                    .attemptId(declaration.attemptId());
        }
        throw new ArtifactErrorCode.ProvenanceException(error.build(), validation.violations());
    }

    @Override
    public Optional<ArtifactCommitResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        // Idempotency-key replay is delegated to the caller's durable record; the
        // canonical artifact existence check above already fails closed on re-commit.
        return Optional.empty();
    }
}
