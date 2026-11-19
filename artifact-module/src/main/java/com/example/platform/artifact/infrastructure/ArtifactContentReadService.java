package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArtifactContentReadService implements ArtifactContentRead {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactContentReadService.class);
    private static final long MAX_CONTENT_BYTES = 64L * 1024 * 1024;
    private static final long DIAGNOSTIC_INTERVAL_MILLIS = 5_000L;

    private final ArtifactApplicationQuery scoped;
    private final ArtifactQueryService artifacts;
    private final VerifiedStorageRead storage;
    private final StoragePlacementQuery placements;
    private final AtomicLong lastDiagnosticAt = new AtomicLong(0);

    public ArtifactContentReadService(ArtifactApplicationQuery scoped, ArtifactQueryService artifacts,
            VerifiedStorageRead storage, StoragePlacementQuery placements) {
        this.scoped = scoped;
        this.artifacts = artifacts;
        this.storage = storage;
        this.placements = placements;
    }

    @Override
    public Content read(ArtifactScope scope, ArtifactId id) {
        TenantGuard.assertSameTenant(scope.tenantId());
        var artifact = scoped.findArtifact(scope, id)
                .orElseThrow(() -> failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND));
        return readVerified(scope.tenantId(), id, artifact);
    }

    @Override
    public Content readProjectArtifact(ArtifactProjectScope scope, ArtifactId id) {
        TenantGuard.assertSameTenant(scope.tenantId());
        var artifact = scoped.findProjectArtifact(scope, id)
                .orElseThrow(() -> failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND));
        if (artifact.artifactKind() == ArtifactKind.SOURCE_MEDIA) {
            return readProjectSource(scope, id, artifact);
        }
        return readVerified(scope.tenantId(), id, artifact);
    }

    @Override
    public Content readProjectSourceArtifact(ArtifactProjectScope scope, ArtifactId id) {
        TenantGuard.assertSameTenant(scope.tenantId());
        var artifact = scoped.findProjectArtifact(scope, id)
                .orElseThrow(() -> failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND));
        if (artifact.artifactKind() != ArtifactKind.SOURCE_MEDIA) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND);
        }
        return readProjectSource(scope, id, artifact);
    }

    private Content readProjectSource(ArtifactProjectScope scope, ArtifactId id, Artifact artifact) {
        if (artifact.artifactKind() != ArtifactKind.SOURCE_MEDIA) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND);
        }
        if (artifact.state() != ArtifactState.AVAILABLE) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        }
        if (artifact.byteLength() > MAX_CONTENT_BYTES) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        }

        var owner = new StorageOwnershipScope(scope.tenantId(), scope.projectId());
        var reasons = EnumSet.noneOf(SourceFailure.class);
        var replicas = new ArrayList<>(artifacts.listReplicas(scope.tenantId(), id));
        // Prefer a provider the application can read directly, then keep a deterministic order.
        replicas.sort(Comparator
                .comparing((ArtifactReplicaBinding replica) -> !storage.supports(replica.providerId()))
                .thenComparing(replica -> replica.providerId().value())
                .thenComparing(replica -> replica.storageReplicaId().value()));
        if (replicas.isEmpty()) {
            reasons.add(SourceFailure.PLACEMENT_MISSING);
        }

        for (var replica : replicas) {
            try {
                final byte[] bytes;
                if (storage.supports(replica.providerId())) {
                    bytes = storage.read(replica.providerId(), replica.storageObjectId(),
                            artifact.contentDigest(), artifact.byteLength(), MAX_CONTENT_BYTES);
                } else {
                    reasons.add(SourceFailure.PROVIDER_UNREGISTERED);
                    bytes = readCommittedSource(owner, replica, artifact, reasons);
                }
                return verifiedContent(bytes, artifact);
            } catch (SourceFailureException unavailable) {
                reasons.add(unavailable.reason());
            } catch (RuntimeException unavailable) {
                reasons.add(SourceFailure.OBJECT_UNAVAILABLE);
            }
        }

        diagnose(scope, id, reasons);
        throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
    }

    /**
     * Worker-parity fallback for SOURCE_MEDIA written through {@code StorageOutputService}/{@code BlobStorage}.
     * The provider registry only covers {@code StorageProvider} beans; the source write authority records a
     * {@code BlobStorage} code, so the committed Storage receipt is the canonical data-plane read here.
     */
    private byte[] readCommittedSource(StorageOwnershipScope owner, ArtifactReplicaBinding replica,
            Artifact artifact, Set<SourceFailure> reasons) {
        final StorageObjectIssuance.IssuanceResult issued;
        try {
            issued = placements.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            reasons.add(SourceFailure.PLACEMENT_MISSING);
            throw new SourceFailureException(SourceFailure.PLACEMENT_MISSING, unavailable);
        }
        if (issued == null) {
            reasons.add(SourceFailure.PLACEMENT_MISSING);
            throw new SourceFailureException(SourceFailure.PLACEMENT_MISSING);
        }

        boolean exact = owner.equals(issued.owner())
                && issued.objectId().equals(replica.storageObjectId())
                && issued.placement().replicaId().equals(replica.storageReplicaId())
                && issued.receipt().purpose() == StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE
                && issued.placement().location().providerId().equals(replica.providerId())
                && owner.tenantId().equals(issued.placement().location().namespace().tenantId())
                && owner.projectId().equals(issued.placement().location().namespace().projectId())
                && issued.placement().location().namespace().namespaceClass() == NamespaceClass.SOURCE
                && issued.placement().state() == ReplicaState.AVAILABLE
                && issued.placement().committedDigest().matches(artifact.contentDigest())
                && issued.placement().committedLength() == artifact.byteLength();
        if (!exact) {
            reasons.add(SourceFailure.PLACEMENT_INVALID);
            throw new SourceFailureException(SourceFailure.PLACEMENT_INVALID);
        }

        try {
            return placements.read(owner, replica.storageObjectId(), replica.storageReplicaId());
        } catch (RuntimeException unavailable) {
            reasons.add(SourceFailure.OBJECT_UNAVAILABLE);
            throw new SourceFailureException(SourceFailure.OBJECT_UNAVAILABLE, unavailable);
        }
    }

    private Content readVerified(String tenantId, ArtifactId id, Artifact artifact) {
        if (artifact.state() != ArtifactState.AVAILABLE) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        }
        if (artifact.byteLength() > MAX_CONTENT_BYTES) {
            throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        }
        for (var replica : artifacts.listReplicas(tenantId, id)) {
            try {
                byte[] bytes = storage.read(replica.providerId(), replica.storageObjectId(),
                        artifact.contentDigest(), artifact.byteLength(), MAX_CONTENT_BYTES);
                return verifiedContent(bytes, artifact);
            } catch (RuntimeException unavailable) {
                // bounded alternate replica; no internal failure leaks
            }
        }
        throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
    }

    /** Single representation authority for digest, length, container and MIME. */
    private Content verifiedContent(byte[] bytes, Artifact artifact) {
        if (bytes.length != artifact.byteLength()) {
            throw new SourceFailureException(SourceFailure.INTEGRITY_MISMATCH);
        }
        final ContentDigest actual;
        try {
            actual = ContentDigest.sha256(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new SourceFailureException(SourceFailure.INTEGRITY_MISMATCH, impossible);
        }
        if (!actual.matches(artifact.contentDigest())) {
            throw new SourceFailureException(SourceFailure.INTEGRITY_MISMATCH);
        }
        for (var format : ArtifactOutputFormat.values()) {
            if (format.mediaType() == artifact.mediaType()) {
                try {
                    format.validate(bytes);
                    return new Content(bytes, format.mimeType(), format.fileName(), artifact.contentDigest());
                } catch (IllegalArgumentException unsupportedFormat) {
                    // Try the next accepted container of the same media type.
                }
            }
        }
        throw new SourceFailureException(SourceFailure.INTEGRITY_MISMATCH);
    }

    private void diagnose(ArtifactProjectScope scope, ArtifactId id, Set<SourceFailure> reasons) {
        long now = System.currentTimeMillis();
        long previous = lastDiagnosticAt.get();
        if (now - previous < DIAGNOSTIC_INTERVAL_MILLIS || !lastDiagnosticAt.compareAndSet(previous, now)) {
            return;
        }
        LOG.warn("artifact source content unavailable tenant={} project={} artifact={} providerUnregistered={} reasons={}",
                scope.tenantId(), scope.projectId(), id.value(),
                reasons.contains(SourceFailure.PROVIDER_UNREGISTERED), reasons);
    }

    private static ArtifactApplicationService.ArtifactAccessException failure(
            ArtifactApplicationService.ArtifactAccessFailure type) {
        return new ArtifactApplicationService.ArtifactAccessException(type,
                type == ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND
                        ? "Artifact not found" : "Artifact content unavailable");
    }

    /** Internal diagnostic categories; never serialized to the HTTP response. */
    private enum SourceFailure {
        PROVIDER_UNREGISTERED,
        PLACEMENT_MISSING,
        PLACEMENT_INVALID,
        OBJECT_UNAVAILABLE,
        INTEGRITY_MISMATCH
    }

    private static final class SourceFailureException extends RuntimeException {
        private final SourceFailure reason;

        private SourceFailureException(SourceFailure reason) {
            super(reason.name());
            this.reason = reason;
        }

        private SourceFailureException(SourceFailure reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }

        private SourceFailure reason() {
            return reason;
        }
    }
}
