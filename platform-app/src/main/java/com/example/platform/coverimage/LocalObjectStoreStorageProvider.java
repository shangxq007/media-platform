package com.example.platform.coverimage;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.CapabilitySupport;
import com.example.platform.storage.contract.provider.ProviderCapability;
import com.example.platform.storage.contract.provider.StorageObjectMetadata;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.read.ByteRange;
import com.example.platform.storage.contract.read.IntegrityRequirement;
import com.example.platform.storage.contract.read.StorageDeletionRequest;
import com.example.platform.storage.contract.read.StorageDeletionResult;
import com.example.platform.storage.contract.read.StorageReadRequest;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
import com.example.platform.storage.contract.write.WriteSessionState;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Minimal <em>production</em> {@link StorageProvider} for the worker role (COVER-PROVIDER-001 defect 1).
 *
 * <p>Bytes live in a local object store: {@code <root>/objects/<objectId>} with staging under
 * {@code <root>/staging}. Reads return the stored bytes (optionally byte-ranged); {@code stat}
 * reports the object's real SHA-256 digest and length; writes stage, verify the digest and atomically
 * publish under an idempotency-key-derived object identity (so a re-run returns the same object —
 * {@code alreadyCommitted=true}). Mutations that this minimal provider does not implement fail closed.
 *
 * <p>This is deliberately slice-local: the platform's {@code BlobStorage} write path used by
 * {@code StorageOutputPort} is untouched, and no Artifact identity is minted here. A platform-level
 * StorageProvider implementation (S3/OpenDAL-backed, shared by every worker role) remains backlog.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class LocalObjectStoreStorageProvider implements StorageProvider {

    private final StorageProviderId providerId;
    private final Path root;
    private final Path objects;
    private final Path staging;
    private final StorageProviderCapabilities capabilities;

    public LocalObjectStoreStorageProvider(
            @Value("${app.cover-image.storage.provider-id:local-object-store}") String providerId,
            @Value("${app.cover-image.storage.root:./.data/cover-image-object-store}") String root) {
        this.providerId = new StorageProviderId(providerId);
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.objects = this.root.resolve("objects");
        this.staging = this.root.resolve("staging");
        this.capabilities = new StorageProviderCapabilities(this.providerId, capabilityMap());
        try {
            Files.createDirectories(objects);
            Files.createDirectories(staging);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot initialize local object store at " + this.root, failure);
        }
    }

    private Map<ProviderCapability, CapabilitySupport> capabilityMap() {
        Map<ProviderCapability, CapabilitySupport> map = new EnumMap<>(ProviderCapability.class);
        for (ProviderCapability capability : ProviderCapability.values()) {
            map.put(capability, CapabilitySupport.UNSUPPORTED);
        }
        for (ProviderCapability capability : new ProviderCapability[] {
                ProviderCapability.STREAMING_READ,
                ProviderCapability.RANGE_READ,
                ProviderCapability.STREAMING_WRITE,
                ProviderCapability.OBJECT_METADATA,
                ProviderCapability.COPY,
                ProviderCapability.DELETE}) {
            map.put(capability, CapabilitySupport.SUPPORTED);
        }
        return map;
    }

    @Override
    public StorageProviderId providerId() {
        return providerId;
    }

    @Override
    public StorageProviderCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public StorageWriteSession beginWrite(
            String writeSessionId, StorageNamespace namespace, ContentDigest expectedDigest,
            long expectedLength) {
        try {
            Files.createDirectories(staging);
            Files.deleteIfExists(staging.resolve(writeSessionId));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot open local staging object", failure);
        }
        return new StorageWriteSession(writeSessionId, writeSessionId, namespace, expectedDigest,
                expectedLength, providerId, WriteSessionState.PENDING);
    }

    @Override
    public void write(StorageWriteSession session, byte[] data, int offset, int length) {
        Path staged = staging.resolve(session.writeSessionId());
        try (OutputStream out = Files.newOutputStream(staged,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            out.write(data, offset, length);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot write local staging object", failure);
        }
    }

    @Override
    public WriteSessionResult completeWrite(StorageWriteSession session, ContentDigest actualDigest) {
        StorageObjectId objectId = objectIdFor(session.idempotencyKey());
        StorageReplicaId replicaId = new StorageReplicaId("rep-" + objectId.value());
        Path published = objects.resolve(objectId.value());
        if (Files.isRegularFile(published)) {
            return new WriteSessionResult(objectId, replicaId, true, session.idempotencyKey());
        }
        Path staged = staging.resolve(session.writeSessionId());
        if (!Files.isRegularFile(staged)) {
            throw new IllegalStateException("local staging object is missing: " + session.writeSessionId());
        }
        ContentDigest observed;
        try {
            observed = digest(staged);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read local staging object", failure);
        }
        if (!observed.matches(actualDigest)) {
            throw new IllegalStateException("staged bytes do not match the reported content digest");
        }
        try {
            Files.createDirectories(objects);
            try {
                Files.move(staged, published, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, published, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot publish local object", failure);
        }
        return new WriteSessionResult(objectId, replicaId, false, session.idempotencyKey());
    }

    @Override
    public void abortWrite(StorageWriteSession session) {
        try {
            Files.deleteIfExists(staging.resolve(session.writeSessionId()));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot abort local staging object", failure);
        }
    }

    /**
     * Returns the stored bytes. Artifact content-digest verification is performed by the canonical
     * worker-local materialization cache against the Artifact pin, so this provider only resolves the
     * object and honours an optional byte range.
     */
    @Override
    public Optional<InputStream> openRead(StorageReadRequest request) {
        Path object = objects.resolve(request.objectId().value());
        if (!Files.isRegularFile(object)) {
            return Optional.empty();
        }
        try {
            if (request.byteRange().isPresent()) {
                ByteRange range = request.byteRange().orElseThrow();
                long size = Files.size(object);
                long start = Math.min(range.startInclusive(), size);
                long endInclusive = Math.min(range.endInclusive(), size - 1);
                if (endInclusive < start) {
                    return Optional.of(InputStream.nullInputStream());
                }
                InputStream stream = Files.newInputStream(object);
                stream.skipNBytes(start);
                return Optional.of(new BoundedInputStream(stream, endInclusive - start + 1));
            }
            return Optional.of(Files.newInputStream(object));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read local object " + request.objectId(), failure);
        }
    }

    @Override
    public Optional<StorageObjectMetadata> stat(StorageObjectId objectId) {
        Path object = objects.resolve(objectId.value());
        if (!Files.isRegularFile(object)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StorageObjectMetadata(objectId, digest(object), Files.size(object)));
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot stat local object " + objectId, failure);
        }
    }

    @Override
    public StorageReplicaId copy(
            StorageObjectId source, StorageObjectId target, StorageNamespace targetNamespace) {
        Path from = objects.resolve(source.value());
        Path to = objects.resolve(target.value());
        if (!Files.isRegularFile(from)) {
            throw new IllegalArgumentException("source object is unavailable: " + source.value());
        }
        try {
            Files.createDirectories(objects);
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot copy local object", failure);
        }
        return new StorageReplicaId("rep-" + target.value());
    }

    @Override
    public StorageDeletionResult delete(StorageDeletionRequest request) {
        Path object = objects.resolve(request.objectId().value());
        boolean existed = Files.isRegularFile(object);
        try {
            Files.deleteIfExists(object);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot delete local object", failure);
        }
        return new StorageDeletionResult(request.objectId(), existed, !existed);
    }

    @Override
    public HealthStatus health() {
        return new HealthStatus(Files.isWritable(root),
                Files.isWritable(root) ? "local object store writable" : "local object store not writable");
    }

    /** Deterministic object identity for one idempotency key (re-runs reuse the same object). */
    private static StorageObjectId objectIdFor(String idempotencyKey) {
        return new StorageObjectId("obj-" + UUID.nameUUIDFromBytes(
                idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static ContentDigest digest(Path path) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    sha256.update(buffer, 0, read);
                }
            }
        }
        return ContentDigest.sha256(HexFormat.of().formatHex(sha256.digest()));
    }

    /** Bounded view over an underlying stream; never reads past the requested range. */
    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int allowed = (int) Math.min(length, remaining);
            int read = delegate.read(buffer, offset, allowed);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
