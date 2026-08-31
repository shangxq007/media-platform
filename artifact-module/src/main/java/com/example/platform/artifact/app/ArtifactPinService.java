package com.example.platform.artifact.app;

import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C10/C14): Artifact-owned pin protection
 * API. Timeline revision authority registers required Artifact pins through
 * this service INSIDE the revision transaction (atomic: a successful revision
 * commit cannot exist without all required protection rows). Artifact lifecycle
 * (GC/delete) consults the same pins to fail closed.
 */
@Service
public class ArtifactPinService {

    private final ArtifactPinRepository pinRepository;

    public ArtifactPinService(ArtifactPinRepository pinRepository) {
        this.pinRepository = pinRepository;
    }

    /**
     * Register historical-revision protection for the given artifact pins.
     * Idempotent per (revisionId, artifactId): duplicate pins for the same
     * revision are collapsed (UNIQUE(revision_id, artifact_id)).
     */
    public void registerRevisionPins(String projectId, String revisionId,
            String tenantId, List<ArtifactPin> pins) {
        if (pins == null) {
            return;
        }
        Instant now = Instant.now();
        for (ArtifactPin pin : pins) {
            pinRepository.insert(ArtifactPinIdentity.forRevisionArtifact(
                            tenantId, projectId, revisionId, pin.artifactId().value()),
                    revisionId, projectId,
                    tenantId, pin.artifactId().value(), pin.contentDigest(), now);
        }
    }

    /**
     * R4-D1 (CHECKPOINT_A Round 4): transaction-aware registration. The caller
     * (Timeline revision writer) passes its OWN transaction DSL so the pin rows
     * join the SAME physical database transaction as the revision row — no
     * assumption of Spring proxy participation. Artifact remains the pin
     * persistence authority (SQL lives in {@link ArtifactPinRepository}).
     */
    public void registerRevisionPinsTx(org.jooq.DSLContext tx, String projectId,
            String revisionId, String tenantId, List<ArtifactPin> pins) {
        if (pins == null) {
            return;
        }
        Instant now = Instant.now();
        for (ArtifactPin pin : pins) {
            pinRepository.insertTx(tx, ArtifactPinIdentity.forRevisionArtifact(
                            tenantId, projectId, revisionId, pin.artifactId().value()),
                    revisionId, projectId,
                    tenantId, pin.artifactId().value(), pin.contentDigest(), now);
        }
    }

    /**
     * R4-D1: copy the exact pin contract of a historical revision onto a NEW
     * revision id — inside the caller's transaction. Restore must not re-resolve
     * mutable latest Artifact state; the historical immutable pin records are
     * the contract. Artifact owns the SQL.
     */
    public void copyRevisionPinsTx(org.jooq.DSLContext tx, String tenantId, String projectId,
            String fromRevisionId, String toRevisionId) {
        pinRepository.copyPinsTx(tx, tenantId, projectId, fromRevisionId, toRevisionId);
    }

    public boolean isPinned(ArtifactId artifactId) {
        return pinRepository.isPinned(artifactId.value());
    }

    public List<String> listPinnedArtifactIds() {
        return pinRepository.listPinnedArtifactIds();
    }

    public record ArtifactPin(ArtifactId artifactId, ContentDigest contentDigest) {}
}
