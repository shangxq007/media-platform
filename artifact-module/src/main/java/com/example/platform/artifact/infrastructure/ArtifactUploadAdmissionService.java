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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

/** Source-media admission through the existing Storage receipt and Artifact commit authorities. */
@Service
public class ArtifactUploadAdmissionService implements ArtifactUploadAdmissionPort {
    private final StoragePlacementQuery storage;
    private final ArtifactCommitService commits;
    private final ObjectProvider<ArtifactSourceProbePort> probe;
    private final ArtifactSourceFactsStore facts;

    public ArtifactUploadAdmissionService(StoragePlacementQuery storage, ArtifactCommitService commits,
            ObjectProvider<ArtifactSourceProbePort> probe, ArtifactSourceFactsStore facts) {
        this.storage = storage;
        this.commits = commits;
        this.probe = probe;
        this.facts = facts;
    }

    @Override
    @Transactional
    public AdmissionResult admit(ArtifactUploadAdmission admission) {
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
        byte[] bytes = storage.read(owner, new IssuanceIdempotencyKey(admission.storage().receiptKey()));
        var format = ArtifactOutputFormat.require(mimeType);
        format.validate(bytes);
        if (format.mediaType() != admission.technicalMetadata().mediaType()) {
            throw new IllegalArgumentException("source media type does not match bytes");
        }
        ArtifactId id = new ArtifactId("art-" + UUID.nameUUIDFromBytes((
                owner.tenantId() + "\0" + owner.projectId() + "\0" + admission.idempotencyKey())
                .getBytes(StandardCharsets.UTF_8)));
        ArtifactSourceFacts structure = probe.getObject().probe(id, bytes, mimeType);
        ArtifactSourceFacts.Kind expectedKind = switch (format.mediaType()) {
            case VIDEO -> ArtifactSourceFacts.Kind.VIDEO;
            case AUDIO -> ArtifactSourceFacts.Kind.AUDIO;
            default -> throw new IllegalArgumentException("source upload requires timed video or audio");
        };
        if (structure.streams().stream().noneMatch(stream -> stream.kind() == expectedKind)) {
            throw new IllegalArgumentException("source has no matching timed stream");
        }
        var accepted = commits.commit(new ArtifactCommitRequest(
                id, owner.tenantId(), admission.digest(), admission.sizeBytes(),
                format.mediaType(), ArtifactKind.SOURCE_MEDIA, Artifact.CURRENT_SCHEMA_VERSION,
                issuance.objectId(), placement.replicaId(), placement.location().providerId(),
                ReplicaRole.PRIMARY, placement.location().region(),
                "source-upload:" + admission.idempotencyKey(), List.of(),
                issuance.receipt().issuedAt(), issuance.receipt().issuedAt(),
                null, owner.projectId(), admission.scope().workspaceId()));
        if (!id.equals(accepted.artifact().artifactId())) {
            throw new IllegalStateException("source Artifact identity differs from accepted commit");
        }
        facts.record(admission.scope(), id, admission.digest(), structure);
        return new AdmissionResult(id, ArtifactContractFingerprint.upload(admission));
    }
}
