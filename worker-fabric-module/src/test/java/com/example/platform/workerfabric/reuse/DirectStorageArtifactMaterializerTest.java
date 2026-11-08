package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.*;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.storage.api.*;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.*;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectStorageArtifactMaterializerTest {
    @TempDir Path root;
    final ArtifactQueryService artifacts = mock(ArtifactQueryService.class);
    final StorageProvider first = mock(StorageProvider.class), second = mock(StorageProvider.class);
    final ArtifactId id = new ArtifactId("source");
    final ContentDigest digest = ContentDigest.sha256("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    Artifact artifact(String tenant, ArtifactId id, ContentDigest digest, long length, ArtifactState state) {
        return new Artifact(id, tenant, digest, length, ArtifactMediaType.VIDEO, ArtifactKind.DERIVED_MEDIA, state, 1, Instant.EPOCH);
    }
    ArtifactReplicaBinding replica(ArtifactId artifact, String provider) {
        return new ArtifactReplicaBinding(provider, artifact, new StorageObjectId("object"), new StorageReplicaId("replica"),
                new StorageProviderId(provider), ReplicaRole.PRIMARY, "local", Instant.EPOCH);
    }
    DirectStorageArtifactMaterializer reader() {
        return new DirectStorageArtifactMaterializer(artifacts, Map.of(new StorageProviderId("a"), first, new StorageProviderId("b"), second),
                new WorkerLocalMaterializationCache(root.resolve("cache"), 64));
    }
    void ready(long length) {
        when(artifacts.getArtifact("tenant", id)).thenReturn(Optional.of(artifact("tenant", id, digest, length, ArtifactState.AVAILABLE)));
        when(artifacts.listReplicas("tenant", id)).thenReturn(List.of(replica(id, "a"), replica(id, "b")));
    }
    @Test void verifiedNativeReadNeedsNoLegacyReceiptAndCachedReadStillReauthorizesTenant() throws Exception {
        ready(3); var closed = new AtomicBoolean();
        when(first.openRead(any())).thenReturn(Optional.of(new ByteArrayInputStream("abc".getBytes()) {
            @Override public void close() { closed.set(true); }
        }));
        var reader = reader(); var result = reader.materialize("tenant", new ArtifactPin(id, digest));
        assertEquals(3, result.materializedArtifact().byteLength()); assertTrue(closed.get());
        assertArrayEquals("abc".getBytes(), Files.readAllBytes(result.materializedArtifact().path()));
        when(artifacts.getArtifact("other-tenant", id)).thenReturn(Optional.empty());
        assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("other-tenant", new ArtifactPin(id, digest)));
        verify(first, times(1)).openRead(any()); verifyNoInteractions(second);
    }
    @Test void wrongTenantArtifactPinOrUnavailableStateCannotReachStorage() throws Exception {
        var reader = reader();
        for (var value : List.of(artifact("other", id, digest, 3, ArtifactState.AVAILABLE),
                artifact("tenant", new ArtifactId("other"), digest, 3, ArtifactState.AVAILABLE),
                artifact("tenant", id, ContentDigest.sha256("f".repeat(64)), 3, ArtifactState.AVAILABLE),
                artifact("tenant", id, digest, 3, ArtifactState.REGISTERING))) {
            when(artifacts.getArtifact("tenant", id)).thenReturn(Optional.of(value));
            assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("tenant", new ArtifactPin(id, digest)));
        }
        verify(artifacts, never()).listReplicas(anyString(), any()); verifyNoInteractions(first, second);
    }
    @Test void replicaForAnotherArtifactCannotReachAnyProvider() throws Exception {
        ready(3); when(artifacts.listReplicas("tenant", id)).thenReturn(List.of(replica(new ArtifactId("other"), "a")));
        assertThrows(ArtifactMaterializationException.class, () -> reader().materialize("tenant", new ArtifactPin(id, digest)));
        verifyNoInteractions(first, second);
    }
    @Test void failedNativeReadDoesNotFallThroughToAnotherProviderOrProtocol() throws Exception {
        ready(3); var failure = new IllegalStateException("synthetic native failure");
        when(first.openRead(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> reader().materialize("tenant", new ArtifactPin(id, digest))));
        verifyNoInteractions(second);
    }
    @Test void metadataLengthMismatchCannotYieldAnExecutableInputHandle() throws Exception {
        ready(4); when(first.openRead(any())).thenReturn(Optional.of(new ByteArrayInputStream("abc".getBytes())));
        var failure = assertThrows(ArtifactMaterializationException.class, () -> reader().materialize("tenant", new ArtifactPin(id, digest)));
        assertEquals("Artifact byte length mismatch", failure.getMessage()); verifyNoInteractions(second);
    }

    @Test void scopedCommittedSourceReadRevalidatesReceiptOnCacheHit() throws Exception {
        var catalog = mock(ArtifactCatalogService.class);
        var storage = mock(StoragePlacementQuery.class);
        var owner = new StorageOwnershipScope("tenant", "project");
        var replica = replica(id, "blob-backend");
        var namespace = new StorageNamespace("tenant", "project", NamespaceClass.SOURCE,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        var location = new StorageObjectLocation(replica.providerId(), namespace, "opaque", null, "local");
        var placement = new StorageObjectIssuance.BackendPlacementResult(replica.storageReplicaId(),
                location, ReplicaState.AVAILABLE, digest, 3, "correlation");
        var receipt = new StorageObjectIssuance.PlacementReceipt("receipt", new IssuanceIdempotencyKey("key"),
                "a".repeat(64), StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE,
                replica.storageObjectId(), replica.storageReplicaId(), location, ReplicaState.AVAILABLE,
                digest, 3, "correlation", Instant.EPOCH);
        var issuance = new StorageObjectIssuance.IssuanceResult(owner, replica.storageObjectId(), placement, receipt);
        when(artifacts.getArtifact("tenant", id)).thenReturn(Optional.of(new Artifact(id, "tenant", digest,
                3, ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE, 1, Instant.EPOCH)));
        when(artifacts.listReplicas("tenant", id)).thenReturn(List.of(replica));
        var entry = mock(ArtifactCatalogEntry.class);
        when(entry.isUsable()).thenReturn(true);
        when(catalog.findArtifact("tenant", "project", id.value())).thenReturn(Optional.of(entry));
        when(storage.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn(Optional.of(issuance));
        when(storage.read(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn("abc".getBytes());
        var reader = new DirectStorageArtifactMaterializer(artifacts, Map.of(),
                new WorkerLocalMaterializationCache(root.resolve("source-cache"), 64), catalog, storage);
        assertArrayEquals("abc".getBytes(), Files.readAllBytes(reader.materialize("tenant", "project", new ArtifactPin(id, digest)).materializedArtifact().path()));
        when(storage.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn(Optional.empty());
        assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("tenant", "project", new ArtifactPin(id, digest)));
        assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("tenant", "other", new ArtifactPin(id, digest)));
        verify(storage, times(1)).read(owner, replica.storageObjectId(), replica.storageReplicaId());

        var wrongDigest = ContentDigest.sha256("f".repeat(64));
        var badPlacement = new StorageObjectIssuance.BackendPlacementResult(replica.storageReplicaId(), location,
                ReplicaState.AVAILABLE, wrongDigest, 3, "correlation");
        var badReceipt = new StorageObjectIssuance.PlacementReceipt("receipt", new IssuanceIdempotencyKey("key"),
                "a".repeat(64), StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE,
                replica.storageObjectId(), replica.storageReplicaId(), location, ReplicaState.AVAILABLE,
                wrongDigest, 3, "correlation", Instant.EPOCH);
        when(storage.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn(Optional.of(
                new StorageObjectIssuance.IssuanceResult(owner, replica.storageObjectId(), badPlacement, badReceipt)));
        assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("tenant", "project", new ArtifactPin(id, digest)));
        var foreignNamespace = new StorageNamespace("tenant", "other", NamespaceClass.SOURCE,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        var foreignLocation = new StorageObjectLocation(replica.providerId(), foreignNamespace, "opaque", null, "local");
        var foreignPlacement = new StorageObjectIssuance.BackendPlacementResult(replica.storageReplicaId(), foreignLocation,
                ReplicaState.AVAILABLE, digest, 3, "correlation");
        var foreignReceipt = new StorageObjectIssuance.PlacementReceipt("receipt", new IssuanceIdempotencyKey("key"),
                "a".repeat(64), StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE,
                replica.storageObjectId(), replica.storageReplicaId(), foreignLocation, ReplicaState.AVAILABLE,
                digest, 3, "correlation", Instant.EPOCH);
        when(storage.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn(Optional.of(
                new StorageObjectIssuance.IssuanceResult(owner, replica.storageObjectId(), foreignPlacement, foreignReceipt)));
        assertThrows(ArtifactMaterializationException.class, () -> reader.materialize("tenant", "project", new ArtifactPin(id, digest)));
        when(storage.findPlacement(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn(Optional.of(issuance));
        when(storage.read(owner, replica.storageObjectId(), replica.storageReplicaId())).thenReturn("bad".getBytes());
        var freshReader = new DirectStorageArtifactMaterializer(artifacts, Map.of(),
                new WorkerLocalMaterializationCache(root.resolve("corrupt-cache"), 64), catalog, storage);
        assertThrows(IOException.class, () -> freshReader.materialize("tenant", "project", new ArtifactPin(id, digest)));
    }
}
