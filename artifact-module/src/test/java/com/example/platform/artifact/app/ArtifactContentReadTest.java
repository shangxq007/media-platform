package com.example.platform.artifact.app;

import com.example.platform.artifact.infrastructure.ArtifactContentReadService;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArtifactContentReadTest {
    final ArtifactScope scope = new ArtifactScope("tenant-a", "project-a", "job-a");
    final ArtifactProjectScope projectScope = new ArtifactProjectScope("tenant-a", "project-a");
    final ArtifactId id = new ArtifactId("output-a");
    final ArtifactId sourceId = new ArtifactId("source-a");
    final byte[] bytes = java.util.HexFormat.of().parseHex("000000186674797069736f6d0000020069736f6d6d703432");
    final ContentDigest digest = hash(bytes);
    final StorageObjectId objectId = new StorageObjectId("so-source-a");
    final StorageReplicaId replicaId = new StorageReplicaId("rep-so-source-a");
    final StorageProviderId sourceProvider = new StorageProviderId("localFsStorageProvider");
    final StorageOwnershipScope owner = new StorageOwnershipScope("tenant-a", "project-a");
    final ArtifactApplicationQuery scoped = mock(ArtifactApplicationQuery.class);
    final ArtifactQueryService query = mock(ArtifactQueryService.class);
    final VerifiedStorageRead storage = mock(VerifiedStorageRead.class);
    final StoragePlacementQuery placements = mock(StoragePlacementQuery.class);
    final ArtifactContentReadService service = new ArtifactContentReadService(scoped, query, storage, placements);

    @BeforeEach void setup() { TenantContext.set("tenant-a"); }
    @AfterEach void clear() { TenantContext.clear(); }

    Artifact artifact(ArtifactState state) {
        return new Artifact(id, "tenant-a", digest, bytes.length, ArtifactMediaType.VIDEO,
                ArtifactKind.RENDER_MASTER, state, 1, Instant.EPOCH);
    }

    Artifact sourceArtifact(ArtifactState state) {
        return new Artifact(sourceId, "tenant-a", digest, bytes.length, ArtifactMediaType.VIDEO,
                ArtifactKind.SOURCE_MEDIA, state, 1, Instant.EPOCH);
    }

    ArtifactReplicaBinding sourceBinding() {
        return new ArtifactReplicaBinding("binding-source", sourceId, objectId, replicaId,
                sourceProvider, ReplicaRole.PRIMARY, "local", Instant.EPOCH);
    }

    void ready() {
        when(scoped.findArtifact(scope, id)).thenReturn(Optional.of(artifact(ArtifactState.AVAILABLE)));
        when(query.listReplicas("tenant-a", id)).thenReturn(List.of(new ArtifactReplicaBinding(
                "binding", id, new StorageObjectId("object-a"), new StorageReplicaId("replica-a"),
                new StorageProviderId("local"), ReplicaRole.PRIMARY, "local", Instant.EPOCH)));
    }

    void sourceReady() {
        when(scoped.findProjectArtifact(projectScope, sourceId)).thenReturn(Optional.of(sourceArtifact(ArtifactState.AVAILABLE)));
        when(query.listReplicas("tenant-a", sourceId)).thenReturn(List.of(sourceBinding()));
        when(storage.supports(sourceProvider)).thenReturn(false);
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        when(placements.read(owner, objectId, replicaId)).thenReturn(bytes);
    }

    StorageObjectIssuance.IssuanceResult issuance(StorageOwnershipScope issuanceOwner,
            StorageObjectId issuanceObject, StorageReplicaId issuanceReplica,
            StorageProviderId issuanceProvider, NamespaceClass namespaceClass, ReplicaState state,
            ContentDigest committedDigest, long committedLength,
            StorageObjectIssuance.ReceiptPurpose purpose) {
        StorageNamespace namespace = new StorageNamespace(issuanceOwner.tenantId(),
                issuanceOwner.projectId(), namespaceClass, RegionPolicy.SINGLE_REGION,
                DataClassification.INTERNAL);
        StorageObjectLocation location = new StorageObjectLocation(issuanceProvider, namespace,
                issuanceProvider.value() + "://artifacts/sources/" + issuanceObject.value() + "/output",
                null, "local");
        StorageObjectIssuance.BackendPlacementResult placement =
                new StorageObjectIssuance.BackendPlacementResult(issuanceReplica, location, state,
                        committedDigest, committedLength, "correlation-1");
        StorageObjectIssuance.PlacementReceipt receipt =
                new StorageObjectIssuance.PlacementReceipt("receipt-1",
                        new IssuanceIdempotencyKey("receipt-key-1"), "a".repeat(64), purpose,
                        issuanceObject, issuanceReplica, location, state, committedDigest,
                        committedLength, "correlation-1", Instant.EPOCH);
        return new StorageObjectIssuance.IssuanceResult(issuanceOwner, issuanceObject, placement, receipt);
    }

    private void assertUnavailable(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class, action);
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE, error.failure());
        assertEquals("Artifact content unavailable", error.getMessage());
    }

    // ---- existing render/derived behavior ----

    @Test void exactLogicalScopeReadsVerifiedBytesAndReturnsNoStorageCoordinates() {
        ready();
        when(storage.read(any(), any(), eq(digest), eq((long) bytes.length), anyLong())).thenReturn(bytes);
        var result = service.read(scope, id);
        assertArrayEquals(bytes, result.bytes());
        assertEquals("video/mp4", result.contentType());
        assertEquals("output.mp4", result.fileName());
        byte[] copy = result.bytes();
        copy[0] = 99;
        assertArrayEquals(bytes, result.bytes());
        verify(scoped).findArtifact(scope, id);
        verify(storage).read(new StorageProviderId("local"), new StorageObjectId("object-a"), digest,
                bytes.length, 64L * 1024 * 1024);
        verifyNoInteractions(placements);
    }

    @Test void otherTenantCannotQueryOrReadStorage() {
        TenantContext.set("other");
        assertThrows(RuntimeException.class, () -> service.read(scope, id));
        verifyNoInteractions(scoped, query, storage, placements);
    }

    @Test void otherProjectOrJobIsNotFoundBeforeStorage() {
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> service.read(new ArtifactScope("tenant-a", "other-project", "job-a"), id));
        verifyNoInteractions(query, storage, placements);
    }

    @Test void allNonAvailableStatesFailBeforeReplicaLookup() {
        for (var state : ArtifactState.values()) {
            if (state == ArtifactState.AVAILABLE) continue;
            when(scoped.findArtifact(scope, id)).thenReturn(Optional.of(artifact(state)));
            assertThrows(ArtifactApplicationService.ArtifactAccessException.class, () -> service.read(scope, id));
        }
        verifyNoInteractions(query, storage, placements);
    }

    @Test void corruptMissingOrEscapingPhysicalObjectHasOneRedactedFailure() {
        ready();
        when(storage.read(any(), any(), any(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("private/path/secret"));
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> service.read(scope, id));
        assertEquals("Artifact content unavailable", error.getMessage());
        assertFalse(error.getMessage().contains("private"));
    }

    @Test void wrongLengthOrDigestIsNotDisclosedEvenIfStorageClaimsVerification() {
        ready();
        byte[] altered = bytes.clone();
        altered[altered.length - 1] ^= 1;
        when(storage.read(any(), any(), any(), anyLong(), anyLong())).thenReturn(altered);
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class, () -> service.read(scope, id));
        when(storage.read(any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Arrays.copyOf(bytes, bytes.length - 1));
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class, () -> service.read(scope, id));
    }

    @Test void verifiedBytesWithWrongContainerSignatureAreNotDisclosed() {
        ready();
        when(storage.read(any(), any(), any(), anyLong(), anyLong())).thenReturn(new byte[24]);
        assertThrows(ArtifactApplicationService.ArtifactAccessException.class, () -> service.read(scope, id));
    }

    // ---- SOURCE_MEDIA receipt fallback ----

    @Test void sourceReplicaWithoutProviderUsesCommittedReceipt() {
        sourceReady();
        var result = service.readProjectSourceArtifact(projectScope, sourceId);
        assertArrayEquals(bytes, result.bytes());
        assertEquals("video/mp4", result.contentType());
        assertEquals("output.mp4", result.fileName());
        verify(storage).supports(sourceProvider);
        verify(storage, never()).read(any(), any(), any(), anyLong(), anyLong());
        verify(placements).findPlacement(owner, objectId, replicaId);
        verify(placements).read(owner, objectId, replicaId);
    }

    @Test void oldProjectRouteSharesSourceReceiptFallback() {
        sourceReady();
        var result = service.readProjectArtifact(projectScope, sourceId);
        assertArrayEquals(bytes, result.bytes());
        verify(placements).read(owner, objectId, replicaId);
    }

    @Test void sourceRouteRejectsNonSourceArtifactBeforeStorage() {
        when(scoped.findProjectArtifact(projectScope, id)).thenReturn(Optional.of(artifact(ArtifactState.AVAILABLE)));
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> service.readProjectSourceArtifact(projectScope, id));
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, error.failure());
        verifyNoInteractions(query, storage, placements);
    }

    @Test void sourceRouteRejectsUnavailableSource() {
        when(scoped.findProjectArtifact(projectScope, sourceId))
                .thenReturn(Optional.of(sourceArtifact(ArtifactState.REGISTERING)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verifyNoInteractions(query, storage, placements);
    }

    @Test void sourceRouteRejectsOversizeSource() {
        var oversized = new Artifact(sourceId, "tenant-a", digest, 64L * 1024 * 1024 + 1,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, Instant.EPOCH);
        when(scoped.findProjectArtifact(projectScope, sourceId)).thenReturn(Optional.of(oversized));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verifyNoInteractions(query, storage, placements);
    }

    @Test void missingPlacementFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId)).thenReturn(Optional.empty());
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementIdentityFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, new StorageObjectId("other-object"), replicaId,
                        sourceProvider, NamespaceClass.SOURCE, ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementOwnerFailsClosed() {
        sourceReady();
        var otherOwner = new StorageOwnershipScope("tenant-b", "project-a");
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(otherOwner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementPurposeFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ADDITIONAL_PLACEMENT)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementNamespaceFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.DERIVED, ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementProviderFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId,
                        new StorageProviderId("local-object-store"), NamespaceClass.SOURCE,
                        ReplicaState.AVAILABLE, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementStateFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.FAILED, digest, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementDigestFailsClosed() {
        sourceReady();
        ContentDigest other = hash(new byte[] {1, 2, 3, 4});
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.AVAILABLE, other, bytes.length,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void invalidPlacementLengthFailsClosed() {
        sourceReady();
        when(placements.findPlacement(owner, objectId, replicaId))
                .thenReturn(Optional.of(issuance(owner, objectId, replicaId, sourceProvider,
                        NamespaceClass.SOURCE, ReplicaState.AVAILABLE, digest, bytes.length + 1,
                        StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE)));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
        verify(placements, never()).read(any(), any(), any());
    }

    @Test void receiptReadFailureFailsClosedWithoutDisclosure() {
        sourceReady();
        when(placements.read(owner, objectId, replicaId))
                .thenThrow(new IllegalStateException("private/path/secret"));
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
    }

    @Test void tamperedReceiptBytesFailClosed() {
        sourceReady();
        byte[] altered = bytes.clone();
        altered[altered.length - 1] ^= 1;
        when(placements.read(owner, objectId, replicaId)).thenReturn(altered);
        assertUnavailable(() -> service.readProjectSourceArtifact(projectScope, sourceId));
    }

    @Test void supportedProviderStillUsesVerifiedReadAndNotTheReceiptPath() {
        when(scoped.findProjectArtifact(projectScope, sourceId)).thenReturn(Optional.of(sourceArtifact(ArtifactState.AVAILABLE)));
        var provider = new StorageProviderId("local-object-store");
        when(query.listReplicas("tenant-a", sourceId)).thenReturn(List.of(new ArtifactReplicaBinding(
                "binding-source", sourceId, objectId, replicaId, provider, ReplicaRole.PRIMARY,
                "local", Instant.EPOCH)));
        when(storage.supports(provider)).thenReturn(true);
        when(storage.read(provider, objectId, digest, bytes.length, 64L * 1024 * 1024)).thenReturn(bytes);
        var result = service.readProjectSourceArtifact(projectScope, sourceId);
        assertArrayEquals(bytes, result.bytes());
        verify(storage).read(provider, objectId, digest, bytes.length, 64L * 1024 * 1024);
        verifyNoInteractions(placements);
    }

    @Test void crossProjectSourceIsNotFoundBeforeStorage() {
        when(scoped.findProjectArtifact(new ArtifactProjectScope("tenant-a", "project-b"), sourceId))
                .thenReturn(Optional.empty());
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> service.readProjectSourceArtifact(
                        new ArtifactProjectScope("tenant-a", "project-b"), sourceId));
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, error.failure());
        verifyNoInteractions(query, storage, placements);
    }

    private static ContentDigest hash(byte[] bytes) {
        try {
            return ContentDigest.sha256(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
