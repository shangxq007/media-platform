package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.contract.ArtifactContractFingerprint;
import com.example.platform.artifact.contract.ArtifactUploadAdmission;
import com.example.platform.artifact.contract.ArtifactUploadAdmissionPort;
import com.example.platform.artifact.contract.ArtifactSourceProbePort;
import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactOutputFormat;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.ObjectProvider;

/** Source-media admission through the existing Storage receipt and Artifact commit authorities. */
@Service
public class ArtifactUploadAdmissionService implements ArtifactUploadAdmissionPort {
    private record PreparedSourceProof(ArtifactUploadAdmissionService owner,
            ArtifactUploadAdmission admission, ArtifactId artifactId, ArtifactSourceFacts facts)
            implements PreparedSource {}
    private final StoragePlacementQuery storage;
    private final ArtifactCommitService commits;
    private final ObjectProvider<ArtifactSourceProbePort> probe;
    private final ArtifactSourceFactsStore facts;
    private final TransactionTemplate transaction;

    public ArtifactUploadAdmissionService(StoragePlacementQuery storage, ArtifactCommitService commits,
            ObjectProvider<ArtifactSourceProbePort> probe, ArtifactSourceFactsStore facts,
            PlatformTransactionManager transactionManager) {
        this.storage = storage;
        this.commits = commits;
        this.probe = probe;
        this.facts = facts;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public PreparedSource prepare(ArtifactUploadAdmission admission) {
        TenantGuard.assertSameTenant(admission.scope().tenantId());
        if (admission.technicalMetadata().kind() != ArtifactKind.SOURCE_MEDIA
                || admission.lifecycle().state() != ArtifactState.AVAILABLE
                || !admission.lineage().parents().isEmpty()) {
            throw new IllegalArgumentException("source upload requires available, parentless source media");
        }
        var owner = new StorageOwnershipScope(
                admission.scope().tenantId(), admission.scope().projectId());
        var issuance = storage.find(owner, new IssuanceIdempotencyKey(admission.storage().receiptKey()))
                .orElseThrow(() -> new IllegalArgumentException("source storage receipt unavailable"));
        var placement = issuance.placement();
        if (!owner.equals(issuance.owner())
                || !admission.storage().issuanceId().equals(issuance.receipt().receiptId())
                || !admission.storage().objectId().equals(issuance.objectId())
                || !admission.storage().replicaId().equals(placement.replicaId())
                || !admission.storage().providerId().equals(placement.location().providerId())
                || placement.location().namespace().namespaceClass() != NamespaceClass.SOURCE
                || placement.state() != ReplicaState.AVAILABLE
                || !placement.committedDigest().equals(admission.digest())
                || placement.committedLength() != admission.sizeBytes()) {
            throw new IllegalArgumentException("source storage receipt does not match admission");
        }
        Object mime = admission.technicalMetadata().fields().get("mimeType");
        if (!(mime instanceof String mimeType)) {
            throw new IllegalArgumentException("source MIME type required");
        }
        var format = ArtifactOutputFormat.require(mimeType);
        if (format.mediaType() != admission.technicalMetadata().mediaType()) {
            throw new IllegalArgumentException("source media type does not match MIME");
        }
        ArtifactId id = new ArtifactId("art-" + UUID.nameUUIDFromBytes((
                owner.tenantId() + "\0" + owner.projectId() + "\0" + admission.idempotencyKey())
                .getBytes(StandardCharsets.UTF_8)));
        var receiptKey = new IssuanceIdempotencyKey(admission.storage().receiptKey());
        ArtifactSourceFacts structure = storage.withVerifiedSource(owner, receiptKey, source -> {
            try (var input = Files.newInputStream(source)) {
                format.validate(input, Files.size(source));
            } catch (IOException unavailable) {
                throw new IllegalStateException("verified source signature unavailable", unavailable);
            }
            return probe.getObject().probe(id, source, mimeType);
        });
        ArtifactSourceFacts.Kind expectedKind = switch (format.mediaType()) {
            case VIDEO -> ArtifactSourceFacts.Kind.VIDEO;
            case AUDIO -> ArtifactSourceFacts.Kind.AUDIO;
            default -> throw new IllegalArgumentException("source upload requires timed video or audio");
        };
        if (structure.streams().stream().noneMatch(stream -> stream.kind() == expectedKind)) {
            throw new IllegalArgumentException("source has no matching timed stream");
        }
        return new PreparedSourceProof(this, admission, id, structure);
    }

    @Override
    public AdmissionResult admitPrepared(PreparedSource prepared, ArtifactUploadAdmission admission) {
        if (!(prepared instanceof PreparedSourceProof proof) || proof.owner() != this)
            throw new IllegalArgumentException("source preparation proof unavailable");
        var original = proof.admission();
        if (original.contractVersion() != admission.contractVersion()
                || !original.scope().equals(admission.scope())
                || !original.storage().equals(admission.storage())
                || !original.digest().equals(admission.digest())
                || original.sizeBytes() != admission.sizeBytes()
                || !original.technicalMetadata().equals(admission.technicalMetadata())
                || !original.audit().equals(admission.audit())
                || !original.lifecycle().equals(admission.lifecycle())
                || !original.lineage().equals(admission.lineage())
                || !original.idempotencyKey().equals(admission.idempotencyKey())) {
            throw new IllegalArgumentException("prepared source differs from final admission");
        }
        TenantGuard.assertSameTenant(admission.scope().tenantId());
        var owner = new StorageOwnershipScope(admission.scope().tenantId(), admission.scope().projectId());
        var receiptKey = new IssuanceIdempotencyKey(admission.storage().receiptKey());
        var id = new ArtifactId("art-" + UUID.nameUUIDFromBytes((
                owner.tenantId() + "\0" + owner.projectId() + "\0" + admission.idempotencyKey())
                .getBytes(StandardCharsets.UTF_8)));
        if (!id.equals(proof.artifactId())) throw new IllegalArgumentException("prepared Artifact identity differs");
        var structure = proof.facts();
        var format = ArtifactOutputFormat.require((String) admission.technicalMetadata().fields().get("mimeType"));
        return transaction.execute(status -> {
            var current = storage.find(owner, receiptKey)
                    .orElseThrow(() -> new IllegalStateException("source receipt changed before commit"));
            var placement = current.placement();
            if (!current.owner().equals(owner)
                    || !current.receipt().receiptId().equals(admission.storage().issuanceId())
                    || !current.objectId().equals(admission.storage().objectId())
                    || !placement.replicaId().equals(admission.storage().replicaId())
                    || !placement.location().providerId().equals(admission.storage().providerId())
                    || placement.location().namespace().namespaceClass() != NamespaceClass.SOURCE
                    || placement.state() != ReplicaState.AVAILABLE
                    || !current.placement().committedDigest().equals(admission.digest())
                    || current.placement().committedLength() != admission.sizeBytes()) {
                throw new IllegalStateException("source receipt changed before commit");
            }
            var accepted = commits.commit(new ArtifactCommitRequest(
                    id, owner.tenantId(), admission.digest(), admission.sizeBytes(),
                    format.mediaType(), ArtifactKind.SOURCE_MEDIA, Artifact.CURRENT_SCHEMA_VERSION,
                    current.objectId(), placement.replicaId(), placement.location().providerId(),
                    ReplicaRole.PRIMARY, placement.location().region(),
                    "source-upload:" + admission.idempotencyKey(), List.of(),
                    current.receipt().issuedAt(), current.receipt().issuedAt(),
                    null, owner.projectId(), admission.scope().workspaceId()));
            if (!id.equals(accepted.artifact().artifactId())) {
                throw new IllegalStateException("source Artifact identity differs from accepted commit");
            }
            facts.record(admission.scope(), id, admission.digest(), structure);
            return new AdmissionResult(id, ArtifactContractFingerprint.upload(admission));
        });
    }
}
