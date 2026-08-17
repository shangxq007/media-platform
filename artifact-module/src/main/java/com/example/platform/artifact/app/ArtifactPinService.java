package com.example.platform.artifact.app;

import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.shared.Ids;
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
            pinRepository.insert(Ids.newId("pin"), revisionId, projectId,
                    pin.artifactId().value(), pin.contentDigest(), now);
        }
    }

    public boolean isPinned(ArtifactId artifactId) {
        return pinRepository.isPinned(artifactId.value());
    }

    public List<String> listPinnedArtifactIds() {
        return pinRepository.listPinnedArtifactIds();
    }

    public record ArtifactPin(ArtifactId artifactId, ContentDigest contentDigest) {}
}
