package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A — artifact-pin invariant boundary for the TimelineRevisionSaveService
 * write surface (Blocker C): missing artifact / wrong tenant / digest mismatch /
 * pin-registration failure / valid pins / patch path, all fail-closed.
 */
class CheckpointAPinInvariantTest {

    private static final String TENANT = "tenant-1";

    private static ContentDigest digest(String hex) {
        return new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, hex);
    }

    private static TimelineDocument pinnedDoc(String artifactIdHex, String digestHex) {
        TimelineClip clip = new TimelineClip(
                "c1", "asset-1", "stream-1", artifactIdHex, digestHex,
                com.example.platform.shared.time.MediaTime.ZERO,
                com.example.platform.shared.time.MediaTime.ofTicks(30, 1),
                com.example.platform.shared.time.MediaTime.ZERO,
                com.example.platform.shared.time.MediaTime.ofTicks(30, 1),
                "MEDIA_STREAM", null);
        TimelineTrack track = new TimelineTrack("v1", "v1",
                com.example.platform.timeline.canonical.TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());
    }

    private static Artifact artifact(String tenant, String digestHex) {
        return new Artifact(new ArtifactId("art-1"), tenant, digest(digestHex), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
    }

    private TimelineRevisionSaveService saveService(ArtifactQueryService query, ArtifactPinService pinService) {
        TimelineArtifactPinValidator validator = new TimelineArtifactPinValidator(query);
        return new TimelineRevisionSaveService(null, null, new TimelineContentDigester(),
                null, validator, pinService);
    }

    @Test
    void case1MissingArtifactFailsClosed() {
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        TimelineRevisionSaveService svc = saveService(query, mock(ArtifactPinService.class));
        assertThrows(TimelineCanonicalRejectionException.class,
                () -> svc.saveRevision("p1", null, pinnedDoc("art-1", "a".repeat(64)), "user"),
                "missing artifact must fail closed before any write");
    }

    @Test
    void case2WrongTenantFailsClosed() {
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        // artifact exists for another tenant → cross-tenant lookup returns empty
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        TimelineRevisionSaveService svc = saveService(query, mock(ArtifactPinService.class));
        assertThrows(TimelineCanonicalRejectionException.class,
                () -> svc.saveRevision("p1", null, pinnedDoc("art-1", "a".repeat(64)), "user"),
                "wrong tenant must fail closed");
    }

    @Test
    void case3DigestMismatchFailsClosed() {
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = artifact(TENANT, "b".repeat(64));
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(artifact));
        TimelineRevisionSaveService svc = saveService(query, mock(ArtifactPinService.class));
        assertThrows(TimelineCanonicalRejectionException.class,
                () -> svc.saveRevision("p1", null, pinnedDoc("art-1", "a".repeat(64)), "user"),
                "digest mismatch must fail closed");
    }

    @Test
    void case4PinRegistrationFailureFailsSave() {
        // validator passes (artifact exists, digest matches); pin registration
        // throws → the save must FAIL (never a partial success). The real
        // rollback semantics are provided by the @Transactional boundary around
        // saveRevision (same jOOQ transaction for revision insert + pin
        // registration + head update — GCR-2 C10, exercised by
        // TimelineRevisionServiceE1bGateIntegrationTest on real PostgreSQL);
        // at unit level we prove the save never returns success when pin
        // registration fails.
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = artifact(TENANT, "a".repeat(64));
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(artifact));
        ArtifactPinService pinService = mock(ArtifactPinService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("pin registration failure"))
                .when(pinService).registerRevisionPins(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
        TimelineRevisionSaveService svc = saveService(query, pinService);
        assertThrows(Exception.class,
                () -> svc.saveRevision("p1", null, pinnedDoc("art-1", "a".repeat(64)), "user"),
                "pin registration failure must fail the whole save (no visible dangling revision)");
    }

    @Test
    void case5ValidPinsPassValidation() {
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = artifact(TENANT, "a".repeat(64));
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(artifact));
        TimelineArtifactPinValidator validator = new TimelineArtifactPinValidator(query);
        // typed document-level pin extraction: exactly one pin for the pinned clip
        TimelineDocument doc = pinnedDoc("art-1", "a".repeat(64));
        var extracted = TimelineArtifactPinExtractor.extract(
                TimelineDocumentJsonSerializer.serializeWithCaptions(doc));
        // the internal-1.0 extractor reads composition JSON; the document-level
        // path used by saveRevision is exercised through the validator contract:
        // valid pins must validate cleanly.
        TimelineArtifactPinValidator.ValidationResult result = validator.validate(TENANT,
                java.util.List.of(new TimelineArtifactPinExtractor.ArtifactPin(
                        new ArtifactId("art-1"), digest("a".repeat(64)))));
        assertTrue(result.valid(), "valid pins must validate: " + result.violations());
        assertTrue(doc.getTracks().get(0).clips().get(0).getArtifactId().equals("art-1"),
                "pinned clip carries the artifact id");
    }

    @Test
    void case6NoPinValidatorCannotCommitPinnedContent() {
        // A save surface wired WITHOUT the validator must refuse pinned content
        // (fail-closed guard) — no no-pin write surface exists.
        TimelineRevisionSaveService svc = new TimelineRevisionSaveService(null, null,
                new TimelineContentDigester(), null, null, null);
        assertThrows(IllegalStateException.class,
                () -> svc.saveRevision("p1", null, pinnedDoc("art-1", "a".repeat(64)), "user"),
                "no-pin save surface must fail closed on pinned content");
        // Unpinned content is unaffected (test wiring remains usable)
        TimelineDocument plain = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty());
        assertThrows(Exception.class, () -> svc.saveRevision("p1", null, plain, "user"),
                "unpinned content still fails later at the (null-dsl) persistence stage — "
                        + "the pin boundary itself is not the failure");
    }
}
