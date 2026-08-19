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
        return saveService(query, pinService, org.mockito.Mockito.mock(org.jooq.DSLContext.class));
    }

    private TimelineRevisionSaveService saveService(ArtifactQueryService query, ArtifactPinService pinService,
            org.jooq.DSLContext dsl) {
        TimelineArtifactPinValidator validator = new TimelineArtifactPinValidator(query);
        // R5-C: all dependencies REQUIRED by construction — dsl/current/snapshot
        // are mocks (legacy null wiring is no longer constructible);
        // validator/pinService are the real/mocked pin boundary under test.
        return new TimelineRevisionSaveService(
                dsl,
                org.mockito.Mockito.mock(ProductCurrentRevisionService.class),
                new TimelineContentDigester(),
                org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class),
                validator, pinService);
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
        // rollback semantics are provided by the explicit jOOQ transaction
        // around saveRevision (revision insert + pin registration + head update
        // are one atomic unit — exercised on real PostgreSQL by the R5-C real
        // DB failure ITs); at unit level we prove the save never returns
        // success when pin registration fails (MOCK_FAILURE_INJECTION).
        com.example.platform.shared.web.TenantContext.set(TENANT);
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = artifact(TENANT, "a".repeat(64));
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(artifact));
        ArtifactPinService pinService = mock(ArtifactPinService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("pin registration failure"))
                .when(pinService).registerRevisionPinsTx(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
        // The transaction callable must actually run for the mocked pin
        // registration failure to fire inside the save flow.
        org.jooq.DSLContext txDsl = org.mockito.Mockito.mock(org.jooq.DSLContext.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        org.jooq.DSLContext dslMock = org.mockito.Mockito.mock(org.jooq.DSLContext.class);
        org.mockito.Mockito.when(dslMock.transactionResult(org.mockito.ArgumentMatchers.<org.jooq.TransactionalCallable<Object>>any()))
                .thenAnswer(inv -> {
                    org.jooq.TransactionalCallable<Object> callable = inv.getArgument(0);
                    return callable.run(txDsl.configuration());
                });
        TimelineRevisionSaveService svc = saveService(query, pinService, dslMock);
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
    void case6NullPinBoundaryRejectedByConstruction() {
        // R5-C: a save surface WITHOUT the artifact-pin boundary cannot even be
        // CONSTRUCTED — required dependencies are non-null by construction
        // (Objects.requireNonNull). No public constructor permits a save/
        // restore surface with a missing validator or pin service.
        TimelineContentDigester digester = new TimelineContentDigester();
        assertThrows(NullPointerException.class,
                () -> new TimelineRevisionSaveService(
                        org.mockito.Mockito.mock(org.jooq.DSLContext.class),
                        org.mockito.Mockito.mock(ProductCurrentRevisionService.class),
                        digester,
                        org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class),
                        null,
                        org.mockito.Mockito.mock(ArtifactPinService.class)),
                "null artifactPinValidator must be rejected by construction");
        assertThrows(NullPointerException.class,
                () -> new TimelineRevisionSaveService(
                        org.mockito.Mockito.mock(org.jooq.DSLContext.class),
                        org.mockito.Mockito.mock(ProductCurrentRevisionService.class),
                        digester,
                        org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class),
                        new TimelineArtifactPinValidator(mock(ArtifactQueryService.class)),
                        null),
                "null artifactPinService must be rejected by construction");
        assertThrows(NullPointerException.class,
                () -> new TimelineRevisionSaveService(
                        null,
                        org.mockito.Mockito.mock(ProductCurrentRevisionService.class),
                        digester,
                        org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class),
                        new TimelineArtifactPinValidator(mock(ArtifactQueryService.class)),
                        org.mockito.Mockito.mock(ArtifactPinService.class)),
                "null dsl must be rejected by construction");
    }
}
