package com.example.platform.artifact.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link ArtifactCommitService} for testing and development.
 *
 * <p>Provides the same transactional semantics as a database-backed implementation:
 * atomic creation, idempotency, and conflict detection.
 */
public class InMemoryArtifactCommitService implements ArtifactCommitService {

    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ArtifactReplicaBinding> replicaBindings = new ConcurrentHashMap<>();
    private final Map<String, ProvenanceEdge> edges = new ConcurrentHashMap<>();
    private final Map<String, ArtifactCommitResult> idempotencyIndex = new ConcurrentHashMap<>();
    private final Map<String, String> artifactTenants = new ConcurrentHashMap<>();

    @Override
    public ArtifactCommitResult commit(ArtifactCommitRequest request) {
        Objects.requireNonNull(request, "request");

        // Idempotency check: same tenant + idempotencyKey
        String idemKey = request.tenantId() + ":" + request.idempotencyKey();
        ArtifactCommitResult previous = idempotencyIndex.get(idemKey);
        if (previous != null) {
            // Verify canonical request matches (compare request fields, not artifact fields)
            String previousRequestDigest = CanonicalSerializer.sha256Hex(
                    previous.artifact().tenantId() +
                    previous.artifact().contentDigest().canonicalValue() +
                    previous.artifact().byteLength());
            String currentRequestDigest = CanonicalSerializer.sha256Hex(
                    request.tenantId() +
                    request.contentDigest().canonicalValue() +
                    request.byteLength());
            if (!previousRequestDigest.equals(currentRequestDigest)) {
                throw new ArtifactErrorCode.ArtifactDomainException(
                        ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_IDEMPOTENCY_CONFLICT)
                                .tenantId(request.tenantId())
                                .artifactId(request.artifactId().value())
                                .expected(previous.artifact().contentDigest().canonicalValue())
                                .actual(request.contentDigest().canonicalValue())
                                .build()
                );
            }
            return previous;
        }

        // Check for artifact already exists
        if (artifacts.containsKey(request.artifactId().value())) {
            throw new ArtifactErrorCode.ArtifactDomainException(
                    ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_ALREADY_EXISTS)
                            .tenantId(request.tenantId())
                            .artifactId(request.artifactId().value())
                            .build()
            );
        }

        // Create artifact (starts as REGISTERING)
        Artifact artifact = new Artifact(
                request.artifactId(),
                request.tenantId(),
                request.contentDigest(),
                request.byteLength(),
                request.mediaType(),
                request.artifactKind(),
                ArtifactState.REGISTERING,
                request.schemaVersion(),
                request.createdAt()
        );

        // Create replica binding
        ArtifactReplicaBinding binding = new ArtifactReplicaBinding(
                "binding-" + request.artifactId().value(),
                request.artifactId(),
                request.storageObjectId(),
                request.storageReplicaId(),
                request.providerId(),
                request.replicaRole(),
                request.region(),
                request.createdAt()
        );

        // Transition to AVAILABLE (valid: REGISTERING -> AVAILABLE)
        if (!ArtifactStateMachine.canTransition(ArtifactState.REGISTERING, ArtifactState.AVAILABLE)) {
            throw new ArtifactErrorCode.ArtifactDomainException(
                    ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_STATE_INVALID)
                            .tenantId(request.tenantId())
                            .artifactId(request.artifactId().value())
                            .expected("REGISTERING -> AVAILABLE")
                            .actual("transition rejected by state machine")
                            .build()
            );
        }
        artifact = artifact.withState(ArtifactState.AVAILABLE);

        // Store artifact in tenants map BEFORE validating provenance edges
        // (so the child endpoint can be found during validation)
        artifactTenants.put(artifact.artifactId().value(), artifact.tenantId());

        // Validate provenance edges (tenant isolation, self-reference, etc.)
        List<ProvenanceEdge> edgeResults = new ArrayList<>();
        int edgeCounter = 0;
        for (ArtifactCommitRequest.ProvenanceEdgeDeclaration decl : request.provenanceDeclarations()) {
            // Self-reference check
            if (decl.parentArtifactId().value().equals(request.artifactId().value())) {
                throw new ArtifactErrorCode.ProvenanceException(
                        ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_SELF_REFERENCE)
                                .tenantId(request.tenantId())
                                .artifactId(request.artifactId().value())
                                .parentArtifactId(decl.parentArtifactId().value())
                                .childArtifactId(request.artifactId().value())
                                .build(),
                        List.of("parent == child == " + decl.parentArtifactId().value())
                );
            }

            // Verify parent exists and belongs to same tenant
            String parentTenant = artifactTenants.get(decl.parentArtifactId().value());
            if (parentTenant == null) {
                throw new ArtifactErrorCode.ProvenanceException(
                        ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND)
                                .tenantId(request.tenantId())
                                .artifactId(request.artifactId().value())
                                .parentArtifactId(decl.parentArtifactId().value())
                                .build(),
                        List.of("Parent artifact not found: " + decl.parentArtifactId().value())
                );
            }
            if (!parentTenant.equals(request.tenantId())) {
                throw new ArtifactErrorCode.ProvenanceException(
                        ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CROSS_TENANT)
                                .tenantId(request.tenantId())
                                .artifactId(request.artifactId().value())
                                .parentArtifactId(decl.parentArtifactId().value())
                                .build(),
                        List.of("Cross-tenant: parent belongs to " + parentTenant)
                );
            }

            ProvenanceEdge edge = new ProvenanceEdge(
                    "edge-" + request.artifactId().value() + "-" + edgeCounter++,
                    request.tenantId(),
                    decl.parentArtifactId(),
                    request.artifactId(),
                    decl.relationType(),
                    decl.operationId(),
                    decl.operationVersion(),
                    decl.attemptId(),
                    decl.requestDigest(),
                    decl.resultDigest(),
                    request.createdAt()
            );

            // Validate edge against existing graph
            ProvenanceValidator.ValidationResult validation = ProvenanceValidator.validateEdge(
                    edge, edges.values(), artifactTenants);
            if (!validation.valid()) {
                throw new ArtifactErrorCode.ProvenanceException(
                        ArtifactErrorCode.Error.builder(ArtifactErrorCode.Code.ARTIFACT_PROVENANCE_CYCLE)
                                .tenantId(request.tenantId())
                                .artifactId(request.artifactId().value())
                                .build(),
                        validation.violations()
                );
            }

            edges.put(edge.edgeId(), edge);
            edgeResults.add(edge);
        }

        // Store all (atomic)
        artifacts.put(artifact.artifactId().value(), artifact);
        replicaBindings.put(binding.bindingId(), binding);

        ArtifactCommitResult result = new ArtifactCommitResult(
                artifact, binding, edgeResults, request.idempotencyKey()
        );
        idempotencyIndex.put(idemKey, result);
        return result;
    }

    @Override
    public Optional<ArtifactCommitResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        String idemKey = tenantId + ":" + idempotencyKey;
        return Optional.ofNullable(idempotencyIndex.get(idemKey));
    }
}
