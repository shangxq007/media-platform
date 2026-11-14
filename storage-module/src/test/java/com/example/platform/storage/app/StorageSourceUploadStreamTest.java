package com.example.platform.storage.app;

import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StorageReferenceStore;
import com.example.platform.storage.api.StorageWriteIntentRecovery;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageReference;
import com.example.platform.storage.infrastructure.LocalFsStorageProvider;
import com.example.platform.storage.infrastructure.StorageS3Properties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageSourceUploadStreamTest {
    @TempDir Path root;
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test void incorrectLengthDoesNotCreateWriteIntentAndRemovesStagingFile() throws Exception {
        TenantContext.set("tenant-a");
        var recovery = mock(StorageWriteIntentRecovery.class);
        var storage = service(recovery);
        assertThrows(IllegalArgumentException.class, () -> storage.writeSource(
                new StorageOwnershipScope("tenant-a", "project-a"), new IssuanceIdempotencyKey("upload-a"),
                new ByteArrayInputStream(new byte[] {1, 2, 3}), 4, "video/mp4"));
        verifyNoInteractions(recovery);
        try (var files = Files.list(root.resolve(".source-upload-staging"))) {
            assertEquals(0, files.count());
        }
    }

    @Test void oversizedDeclarationIsRejectedBeforeReading() {
        TenantContext.set("tenant-a");
        var recovery = mock(StorageWriteIntentRecovery.class);
        InputStream unreadable = new InputStream() {
            @Override public int read() { throw new AssertionError("content must not be read"); }
        };
        assertThrows(IllegalArgumentException.class, () -> service(recovery).writeSource(
                new StorageOwnershipScope("tenant-a", "project-a"), new IssuanceIdempotencyKey("upload-a"),
                unreadable, 64L * 1024 * 1024 + 1, "video/mp4"));
        verifyNoInteractions(recovery);
        assertFalse(Files.exists(root.resolve(".source-upload-staging")));
    }

    @Test void actualStreamBeyondLimitIsRejectedAndStagingIsRemoved() throws Exception {
        TenantContext.set("tenant-a");
        var recovery = mock(StorageWriteIntentRecovery.class);
        InputStream largerThanClaimed = new InputStream() {
            long remaining = 64L * 1024 * 1024 + 1;
            @Override public int read() { return remaining-- > 0 ? 0 : -1; }
            @Override public int read(byte[] target, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(remaining, length);
                java.util.Arrays.fill(target, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> service(recovery).writeSource(
                new StorageOwnershipScope("tenant-a", "project-a"), new IssuanceIdempotencyKey("upload-a"),
                largerThanClaimed, 64L * 1024 * 1024, "video/mp4"));
        verifyNoInteractions(recovery);
        try (var files = Files.list(root.resolve(".source-upload-staging"))) {
            assertEquals(0, files.count());
        }
    }

    @Test void streamedSourceCanBeReadOnlyThroughVerifiedScopedReceipt() throws Exception {
        TenantContext.set("tenant-a");
        var owner = new StorageOwnershipScope("tenant-a", "project-a");
        var key = new IssuanceIdempotencyKey("upload-a");
        var recovery = mock(StorageWriteIntentRecovery.class);
        var receipts = mock(StorageObjectAuthorityRepository.class);
        var references = mock(StorageReferenceStore.class);
        var placement = new AtomicReference<StorageObjectIssuance.BackendPlacementResult>();
        var objectId = new StorageObjectId("object-a");
        when(recovery.beginOrResume(any())).thenAnswer(call -> {
            StorageWriteIntentRecovery.BeginWriteIntentCommand command = call.getArgument(0);
            return new StorageWriteIntent("intent-a", objectId, owner, key,
                    command.semanticFingerprint(), null, null, null,
                    StorageWriteIntent.State.PENDING_PROVIDER, null, null,
                    Instant.EPOCH, Instant.EPOCH, null);
        });
        when(recovery.complete(any())).thenAnswer(call -> {
            StorageWriteIntentRecovery.CompleteWriteIntentCommand command = call.getArgument(0);
            placement.set(command.backendPlacement());
            return null;
        });
        when(receipts.findOriginalIssuance(owner, key)).thenAnswer(call -> {
            var p = placement.get();
            var receipt = new StorageObjectIssuance.PlacementReceipt("receipt-a", key,
                    "a".repeat(64), StorageObjectIssuance.ReceiptPurpose.ORIGINAL_ISSUANCE,
                    objectId, p.replicaId(), p.location(), p.state(), p.committedDigest(),
                    p.committedLength(), p.providerCorrelationId(), Instant.EPOCH);
            return Optional.of(new StorageObjectIssuance.IssuanceResult(owner, objectId, p, receipt));
        });
        when(references.save(any())).thenAnswer(call -> call.getArgument(0, StorageReference.class));
        var storage = new StorageOutputService(new LocalFsStorageProvider(root.toString()),
                recovery, receipts, references, root.toString(),
                new OutputStorageProperties(), new StorageS3Properties());
        byte[] bytes = "source-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var written = storage.writeSource(owner, key, new ByteArrayInputStream(bytes), bytes.length, "video/mp4");
        assertEquals(bytes.length, written.issuance().placement().committedLength());
        assertArrayEquals(bytes, storage.withVerifiedSource(owner, key, path -> {
            try { return Files.readAllBytes(path); }
            catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }));
        try (var files = Files.list(root.resolve(".source-upload-staging"))) {
            assertEquals(0, files.count());
        }
        try (var files = Files.list(root.resolve(".source-read-staging"))) {
            assertEquals(0, files.count());
        }
    }

    private StorageOutputService service(StorageWriteIntentRecovery recovery) {
        return new StorageOutputService(mock(BlobStorage.class), recovery,
                mock(StorageObjectAuthorityRepository.class), mock(StorageReferenceStore.class),
                root.toString(), new OutputStorageProperties(), new StorageS3Properties());
    }
}
