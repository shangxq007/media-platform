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
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import com.example.platform.artifact.domain.ArtifactCommitIdentity;
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
        return dsl.transactionResult(configuration -> {
            var tx=DSL.using(configuration);
            return new JooqArtifactCommitService(new ArtifactRepository(tx),
                    new ArtifactRelationRepository(tx),tx).commitInside(request);
        });
    }

    private ArtifactCommitResult commitInside(ArtifactCommitRequest request) {
        Artifact artifact = new Artifact(
                request.artifactId(),
                request.tenantId(),
                request.contentDigest(),
                request.byteLength(),
                request.mediaType(),
                request.artifactKind(),
                ArtifactState.AVAILABLE,
                request.schemaVersion(),
                request.createdAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));

        // Request-local semantic rejection is deliberately before even the first
        // repository call, so duplicate/self/operation failures cannot degrade to
        // persistence errors or leave partial canonical rows.
        requireValid(request, null, ProvenanceValidator.validateDeclarations(
                artifact.artifactId(), request.provenanceDeclarations()));

        dsl.fetch("select pg_advisory_xact_lock(hashtextextended(?,0))",
                "artifact-commit:"+request.tenantId()+":"+request.idempotencyKey());
        var previous=receipt(request.tenantId(),request.idempotencyKey());
        if (previous.isPresent()) {
            if (!ArtifactCommitIdentity.sameIntent(previous.orElseThrow().request(),request))
                throw conflict(request);
            return previous.orElseThrow().result();
        }
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
                artifact.createdAt());
        artifactRepository.insertReplica(replica);

        for (ProvenanceEdge edge : edges) {
            relationRepository.save(new com.example.platform.artifact.domain.ArtifactRelation(
                    edge.edgeId(),
                    edge.childArtifactId().value(),
                    edge.parentArtifactId().value(),
                    edge.relationType().name()));
        }

        var result=new ArtifactCommitResult(artifact, replica, List.copyOf(edges), request.idempotencyKey());
        try {
            String receipt=JSON.writeValueAsString(new CommitReceipt(request,result));
            int changed=dsl.execute("update artifact set idempotency_key=?, audit_provenance=jsonb_set(audit_provenance,'{artifactCommitV1}',cast(? as jsonb)) where tenant_id=? and id=?",
                    request.idempotencyKey(),receipt,request.tenantId(),request.artifactId().value());
            if(changed!=1)throw new IllegalStateException("Artifact receipt write count");
        } catch(com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode canonical Artifact receipt",error);
        }
        return result;
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
        } else if (validation.declarationErrorContext() != null) {
            ArtifactCommitRequest.ProvenanceEdgeDeclaration declaration =
                    validation.declarationErrorContext().declaration();
            error.parentArtifactId(declaration.parentArtifactId().value())
                    .operationId(declaration.operationId())
                    .attemptId(declaration.attemptId());
        }
        throw new ArtifactErrorCode.ProvenanceException(error.build(), validation.violations());
    }

    @Override
    public Optional<ArtifactCommitResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return receipt(tenantId,idempotencyKey).map(CommitReceipt::result);
    }
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private record CommitReceipt(ArtifactCommitRequest request,ArtifactCommitResult result) {}

    private Optional<CommitReceipt> receipt(String tenant,String key) {
        var rows=dsl.fetch("select project_id, render_job_id, audit_provenance->'artifactCommitV1' as receipt from artifact where tenant_id=? and idempotency_key=?",tenant,key);
        if(rows.isEmpty())return Optional.empty();
        if(rows.size()!=1)throw new IllegalStateException("Ambiguous Artifact idempotency identity");
        try {
            var raw=rows.getFirst().get("receipt",org.jooq.JSONB.class);
            if(raw==null)throw new IllegalStateException("Missing Artifact commit receipt");
            var accepted=JSON.readValue(raw.data(),CommitReceipt.class);
            if(!tenant.equals(accepted.request().tenantId()) || !key.equals(accepted.request().idempotencyKey())
                    || !key.equals(accepted.result().idempotencyKey())
                    || !accepted.request().artifactId().equals(accepted.result().artifact().artifactId())
                    || !Objects.equals(accepted.request().projectId(), rows.getFirst().get("project_id",String.class))
                    || !Objects.equals(accepted.request().renderJobId(), rows.getFirst().get("render_job_id",String.class))
                    || !accepted.request().createdAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).equals(accepted.result().artifact().createdAt())
                    || !ArtifactCommitIdentity.sameIntent(accepted.request(), resultIntent(accepted))
                    || !artifactRepository.findById(tenant,accepted.request().artifactId()).filter(accepted.result().artifact()::equals).isPresent()
                    || !artifactRepository.listReplicas(tenant,accepted.request().artifactId()).contains(accepted.result().replicaBinding()))
                throw new IllegalStateException("Artifact receipt no longer matches canonical identity");
            requireCanonicalProvenance(accepted);
            return Optional.of(accepted);
        } catch(com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Invalid canonical Artifact receipt",error);
        }
    }
    private void requireCanonicalProvenance(CommitReceipt receipt) {
        var request=receipt.request();
        var expected=request.provenanceDeclarations().stream().map(d -> new ProvenanceEdge(
                ProvenanceValidator.canonicalEdgeId(request.artifactId(),d.parentArtifactId()),
                request.tenantId(),d.parentArtifactId(),request.artifactId(),d.relationType(),
                d.operationId(),d.operationVersion(),d.attemptId(),d.requestDigest(),d.resultDigest(),
                request.createdAt())).toList();
        if(!expected.equals(receipt.result().provenanceEdges()))
            throw new IllegalStateException("Artifact receipt provenance differs from declared intent");
        var canonical=relationRepository.findOutgoingForOwnedArtifact(request.tenantId(),request.artifactId().value());
        var relations=expected.stream().map(edge -> new com.example.platform.artifact.domain.ArtifactRelation(
                edge.edgeId(),edge.childArtifactId().value(),edge.parentArtifactId().value(),edge.relationType().name())).toList();
        if(canonical.size()!=relations.size() || !new java.util.HashSet<>(canonical).equals(new java.util.HashSet<>(relations)))
            throw new IllegalStateException("Artifact receipt provenance differs from canonical relations");
        for(var edge:expected) {
            if(artifactRepository.findById(request.tenantId(),edge.parentArtifactId()).isEmpty())
                throw new IllegalStateException("Artifact receipt parent ownership unavailable");
        }
    }
    private static ArtifactCommitRequest resultIntent(CommitReceipt receipt) {
        var a=receipt.result().artifact();var b=receipt.result().replicaBinding();var r=receipt.request();
        if(!a.artifactId().equals(b.artifactId()) || a.state()!=ArtifactState.AVAILABLE)
            throw new IllegalStateException("Invalid Artifact receipt result");
        return new ArtifactCommitRequest(a.artifactId(),a.tenantId(),a.contentDigest(),a.byteLength(),
                a.mediaType(),a.artifactKind(),a.schemaVersion(),b.storageObjectId(),b.storageReplicaId(),
                b.providerId(),b.replicaRole(),b.region(),receipt.result().idempotencyKey(),
                r.provenanceDeclarations(),r.evaluatedAt(),a.createdAt(),r.renderJobId(),r.projectId());
    }
    private static ArtifactErrorCode.ArtifactDomainException conflict(ArtifactCommitRequest request) {
        return new ArtifactErrorCode.ArtifactDomainException(ArtifactErrorCode.Error.builder(
                ArtifactErrorCode.Code.ARTIFACT_IDEMPOTENCY_CONFLICT).tenantId(request.tenantId())
                .artifactId(request.artifactId().value()).build());
    }

}
