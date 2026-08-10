package com.example.platform.storage.opendal.internal;

import com.example.platform.storage.contract.error.StorageError;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.read.*;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
import com.example.platform.storage.contract.write.WriteSessionState;
import com.example.platform.storage.opendal.OpenDalCapabilityMapper;
import com.example.platform.storage.opendal.OpenDalErrorMapper;
import com.example.platform.storage.opendal.OpenDalLocationCodec;
import com.example.platform.storage.opendal.OpenDalStorageException;
import com.example.platform.storage.opendal.OpenDalProviderConfiguration;
import org.apache.opendal.Entry;
import org.apache.opendal.Metadata;
import org.apache.opendal.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstract base for OpenDAL-backed storage providers.
 *
 * <p>Implements the two-phase write protocol using OpenDAL primitives:
 * <ol>
 *   <li>Begin: create staging locator (no physical write yet)</li>
 *   <li>Write: append bytes to staging area</li>
 *   <li>Complete: verify length + digest, then commit by writing to final location</li>
 *   <li>Abort: cleanup staging area</li>
 * </ol>
 *
 * <p>Thread Safety:
 * <ul>
 *   <li>All state maps use ConcurrentHashMap / ConcurrentHashMap.newKeySet()</li>
 *   <li>Guarantee level: IN_PROCESS (per-instance thread safety, not cross-process)</li>
 *   <li>Same idempotency key concurrent complete: exactly one commit wins</li>
 * </ul>
 *
 * <p>All OpenDAL types are confined to this package. The public
 * {@link com.example.platform.storage.contract.provider.StorageProvider} SPI
 * implementation wraps this class without exposing OpenDAL internals.
 */
public abstract class AbstractOpenDalProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenDalProvider.class);

    protected final Operator operator;
    private final StorageProviderId providerId;
    private final StorageProviderCapabilities capabilities;
    private final String serviceType;
    private final String bucket;
    private final OpenDalProviderConfiguration configuration;

    // Thread-safe staging state for in-progress writes (writeSessionId -> staging buffer)
    private final ConcurrentMap<String, byte[]> stagingBuffers = new ConcurrentHashMap<>();
    private final Set<String> committedSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedObjects = ConcurrentHashMap.newKeySet();

    protected AbstractOpenDalProvider(
            Operator operator,
            StorageProviderId providerId,
            String serviceType,
            String bucket,
            OpenDalProviderConfiguration configuration
    ) {
        this.operator = operator;
        this.providerId = providerId;
        this.serviceType = serviceType;
        this.bucket = bucket;
        this.configuration = configuration;

        // Dynamically read capabilities from the OpenDAL backend
        var nativeCap = operator.info.getNativeCapability();
        this.capabilities = new StorageProviderCapabilities(
                providerId,
                OpenDalCapabilityMapper.mapCapability(nativeCap)
        );
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
    public StorageWriteSession beginWrite(String writeSessionId, StorageNamespace namespace,
                                          com.example.platform.storage.contract.ContentDigest expectedDigest,
                                          long expectedLength) {
        Objects.requireNonNull(writeSessionId, "writeSessionId required");
        Objects.requireNonNull(expectedDigest, "expectedDigest required");

        // Initialize staging buffer for this write session
        stagingBuffers.put(writeSessionId, new byte[0]);
        committedSessions.remove(writeSessionId);

        return new StorageWriteSession(
                writeSessionId,
                writeSessionId, // Use writeSessionId as idempotency key for deterministic staging
                namespace,
                expectedDigest,
                expectedLength,
                providerId,
                WriteSessionState.PENDING
        );
    }

    @Override
    public void write(StorageWriteSession session, byte[] data, int offset, int length) {
        Objects.requireNonNull(session, "session required");
        Objects.requireNonNull(data, "data required");

        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException(
                    String.format("Invalid write range: offset=%d, length=%d, data.length=%d", offset, length, data.length)
            );
        }

        String sid = session.writeSessionId();
        byte[] current = stagingBuffers.get(sid);
        if (current == null) {
            throw new IllegalStateException("No staging buffer for session: " + sid);
        }

        byte[] updated = new byte[current.length + length];
        System.arraycopy(current, 0, updated, 0, current.length);
        System.arraycopy(data, offset, updated, current.length, length);
        stagingBuffers.put(sid, updated);
    }

    @Override
    public WriteSessionResult completeWrite(StorageWriteSession session,
                                            com.example.platform.storage.contract.ContentDigest actualDigest) {
        Objects.requireNonNull(session, "session required");
        Objects.requireNonNull(actualDigest, "actualDigest required");

        String sid = session.writeSessionId();

        // Atomically claim commit rights for this session.
        // Only ONE thread gets 'true' from add(); all others return alreadyCommitted.
        if (!committedSessions.add(sid)) {
            return new WriteSessionResult(new StorageReplicaId(sid), true, session.idempotencyKey());
        }

        byte[] data = stagingBuffers.getOrDefault(sid, new byte[0]);

        // Verify byte length matches expected
        if (data.length != session.expectedLength()) {
            committedSessions.remove(sid);
            cleanupStaging(sid);
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_CONTENT_LENGTH_MISMATCH,
                    String.format("Length mismatch: expected=%d, actual=%d", session.expectedLength(), data.length)
            );
        }

        // Verify digest
        if (!actualDigest.matches(session.expectedDigest())) {
            committedSessions.remove(sid);
            cleanupStaging(sid);
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_CONTENT_DIGEST_MISMATCH,
                    String.format("Digest mismatch: expected=%s, actual=%s",
                            session.expectedDigest().canonicalValue(), actualDigest.canonicalValue())
            );
        }

        // Determine commit path
        StorageObjectId objectId = generateObjectId(session);
        String commitPath = OpenDalLocationCodec.pathForObjectId(objectId);

        try {
            ensureParentDirectory(commitPath);
            operator.write(commitPath, data);
            cleanupStaging(sid);
            log.debug("Committed write session {} to path {}", sid, commitPath);
            return new WriteSessionResult(new StorageReplicaId(sid), false, session.idempotencyKey());
        } catch (Exception e) {
            // On failure, release the commit lock so retry can succeed
            committedSessions.remove(sid);
            cleanupStaging(sid);
            StorageError.ErrorCode code = OpenDalErrorMapper.map(e);
            throw new OpenDalStorageException(code, "Failed to commit write: " + e.getMessage(), e);
        }
    }

    @Override
    public void abortWrite(StorageWriteSession session) {
        Objects.requireNonNull(session, "session required");
        String sid = session.writeSessionId();
        cleanupStaging(sid);
        committedSessions.remove(sid);
    }

    @Override
    public Optional<InputStream> openRead(StorageReadRequest request) {
        Objects.requireNonNull(request, "request required");

        String path = OpenDalLocationCodec.pathForObjectId(request.objectId());
        try {
            byte[] data = operator.read(path);

            if (data == null || data.length == 0) {
                return Optional.empty();
            }

            // Apply byte range if specified
            if (request.byteRange().isPresent() && !request.byteRange().get().isFullRange()) {
                ByteRange range = request.byteRange().get();
                int start = (int) range.startInclusive();
                int end = (int) Math.min(range.endInclusive() + 1, data.length);
                if (start >= data.length) {
                    return Optional.empty();
                }
                byte[] ranged = new byte[end - start];
                System.arraycopy(data, start, ranged, 0, ranged.length);
                return Optional.of(new ByteArrayInputStream(ranged));
            }

            return Optional.of(new ByteArrayInputStream(data));
        } catch (Exception e) {
            log.warn("Read failed for object {}: {}", request.objectId().value(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<StorageObjectMetadata> stat(StorageObjectId objectId) {
        Objects.requireNonNull(objectId, "objectId required");

        String path = OpenDalLocationCodec.pathForObjectId(objectId);
        try {
            Metadata meta = operator.stat(path);
            if (meta == null) {
                return Optional.empty();
            }

            long length = meta.getContentLength();
            // Try to get SHA-256 from content and compute directly
            // This avoids ETag parsing issues with non-AWS implementations
            try {
                byte[] data = operator.read(path);
                if (data != null && data.length > 0) {
                    String hex = computeSha256(data);
                    return Optional.of(new StorageObjectMetadata(objectId,
                            com.example.platform.storage.contract.ContentDigest.sha256(hex), length));
                }
            } catch (Exception readEx) {
                // Fall through to ETag-based approach
            }

            // Fallback: try ETag
            String etag = meta.getEtag();
            com.example.platform.storage.contract.ContentDigest digest;
            if (etag != null && !etag.isBlank() && etag.length() == 64) {
                digest = com.example.platform.storage.contract.ContentDigest.sha256(etag);
            } else {
                digest = com.example.platform.storage.contract.ContentDigest.sha256(
                        etag != null ? etag.replaceAll("\"", "") : "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                );
            }

            return Optional.of(new StorageObjectMetadata(objectId, digest, length));
        } catch (Exception e) {
            log.debug("Stat failed for object {}: {}", objectId.value(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public StorageReplicaId copy(StorageObjectId source, StorageObjectId target, StorageNamespace targetNamespace) {
        Objects.requireNonNull(source, "source required");
        Objects.requireNonNull(target, "target required");

        String sourcePath = OpenDalLocationCodec.pathForObjectId(source);
        String targetPath = OpenDalLocationCodec.pathForObjectId(target);

        // Step 1: Read source bytes and capture metadata
        byte[] sourceBytes;
        try {
            sourceBytes = operator.read(sourcePath);
            if (sourceBytes == null || sourceBytes.length == 0) {
                throw new OpenDalStorageException(
                        StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                        "Source object not found or empty: " + source.value());
            }
        } catch (OpenDalStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND,
                    "Source object not found: " + source.value() + " (" + e.getMessage() + ")", e);
        }

        // Step 2: Compute source digest
        String sourceHex = computeSha256(sourceBytes);
        com.example.platform.storage.contract.ContentDigest sourceDigest =
                com.example.platform.storage.contract.ContentDigest.sha256(sourceHex);
        long sourceLength = sourceBytes.length;

        // Step 3: Write to target
        try {
            ensureParentDirectory(targetPath);
            operator.write(targetPath, sourceBytes);
        } catch (Exception e) {
            // Clean up partial target
            cleanupPartialTarget(targetPath);
            StorageError.ErrorCode code = OpenDalErrorMapper.map(e);
            throw new OpenDalStorageException(code, "Copy write failed: " + e.getMessage(), e);
        }

        // Step 4: Verify target integrity
        Metadata targetMeta;
        try {
            targetMeta = operator.stat(targetPath);
            if (targetMeta == null) {
                cleanupPartialTarget(targetPath);
                throw new OpenDalStorageException(
                        StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED,
                        "Target verification failed: target not found after copy");
            }
        } catch (OpenDalStorageException e) {
            cleanupPartialTarget(targetPath);
            throw e;
        } catch (Exception e) {
            cleanupPartialTarget(targetPath);
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED,
                    "Target verification failed: " + e.getMessage(), e);
        }

        // Step 5: Verify target length matches source
        long targetLength = targetMeta.getContentLength();
        if (targetLength != sourceLength) {
            cleanupPartialTarget(targetPath);
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_CONTENT_LENGTH_MISMATCH,
                    String.format("Copy target length mismatch: source=%d, target=%d",
                            sourceLength, targetLength));
        }

        // Step 6: Verify target digest by reading back
        try {
            byte[] targetBytes = operator.read(targetPath);
            String targetHex = computeSha256(targetBytes);
            if (!targetHex.equals(sourceHex)) {
                cleanupPartialTarget(targetPath);
                throw new OpenDalStorageException(
                        StorageError.ErrorCode.STORAGE_CONTENT_DIGEST_MISMATCH,
                        String.format("Copy target digest mismatch: source=%s, target=%s",
                                sourceHex, targetHex));
            }
        } catch (OpenDalStorageException e) {
            cleanupPartialTarget(targetPath);
            throw e;
        } catch (Exception e) {
            cleanupPartialTarget(targetPath);
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_CONTENT_DIGEST_MISMATCH,
                    "Copy target digest verification failed: " + e.getMessage(), e);
        }

        log.debug("Copy verified: source={} ({} bytes) -> target={} ({} bytes)",
                source.value(), sourceLength, target.value(), targetLength);

        return new StorageReplicaId("replica-" + target.value());
    }

    @Override
    public StorageDeletionResult delete(StorageDeletionRequest request) {
        Objects.requireNonNull(request, "request required");

        String path = OpenDalLocationCodec.pathForObjectId(request.objectId());
        // Check if already deleted in this session
        if (deletedObjects.contains(request.objectId().value())) {
            return new StorageDeletionResult(request.objectId(), false, true);
        }
        try {
            // Check if object exists before deleting (filesystem may not throw on missing)
            try {
                Metadata meta = operator.stat(path);
                if (meta == null) {
                    return new StorageDeletionResult(request.objectId(), false, false);
                }
            } catch (Exception statEx) {
                // Object doesn't exist
                return new StorageDeletionResult(request.objectId(), false, false);
            }
            operator.delete(path);
            deletedObjects.add(request.objectId().value());
            return new StorageDeletionResult(request.objectId(), true, false);
        } catch (Exception e) {
            // If the object doesn't exist, return idempotent result
            StorageError.ErrorCode code = OpenDalErrorMapper.map(e);
            if (code == StorageError.ErrorCode.STORAGE_OBJECT_NOT_FOUND) {
                return new StorageDeletionResult(request.objectId(), false, false);
            }
            throw new OpenDalStorageException(code, "Delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public HealthStatus health() {
        try {
            // Simple health check: try to stat root
            operator.stat("");
            return new HealthStatus(true, "OpenDAL operator healthy for " + serviceType);
        } catch (Exception e) {
            return new HealthStatus(false, "OpenDAL operator unhealthy: " + e.getMessage());
        }
    }

    /**
     * Returns the detailed health status distinguishing HEALTHY, UNAVAILABLE, and MISCONFIGURABLE.
     */
    public HealthDetail healthDetail() {
        try {
            operator.stat("");
            return new HealthDetail(HealthLevel.HEALTHY, "OpenDAL operator healthy for " + serviceType);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("config")) {
                return new HealthDetail(HealthLevel.MISCONFIGURABLE,
                        "OpenDAL configuration error: " + msg);
            }
            return new HealthDetail(HealthLevel.UNAVAILABLE,
                    "OpenDAL operator unhealthy: " + msg);
        }
    }

    // ── Protected helpers ──

    protected Operator operator() {
        return operator;
    }

    protected String serviceType() {
        return serviceType;
    }

    protected String bucket() {
        return bucket;
    }

    protected OpenDalProviderConfiguration configuration() {
        return configuration;
    }

    /**
     * Generates a deterministic StorageObjectId from a write session.
     */
    protected StorageObjectId generateObjectId(StorageWriteSession session) {
        return new StorageObjectId("obj-" + session.writeSessionId());
    }

    /**
     * Computes SHA-256 hex digest for given data.
     */
    protected static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new OpenDalStorageException(
                    StorageError.ErrorCode.STORAGE_NOT_IMPLEMENTED,
                    "SHA-256 not available",
                    e
            );
        }
    }

    /**
     * Ensures parent directory exists for the given path.
     * Called before write to prevent "directory not found" errors.
     */
    private void ensureParentDirectory(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            String parent = path.substring(0, lastSlash);
            try {
                operator.createDir(parent + "/");
            } catch (Exception e) {
                // Directory may already exist, ignore
                log.debug("createDir {}: {}", parent, e.getMessage());
            }
        }
    }

    /**
     * Cleans up a partial target after a failed copy verification.
     */
    private void cleanupPartialTarget(String targetPath) {
        try {
            operator.delete(targetPath);
            log.debug("Cleaned up partial target: {}", targetPath);
        } catch (Exception e) {
            log.warn("Failed to clean up partial target {}: {}", targetPath, e.getMessage());
        }
    }

    private void cleanupStaging(String writeSessionId) {
        stagingBuffers.remove(writeSessionId);
    }

    /**
     * Health check level enumeration.
     */
    public enum HealthLevel {
        HEALTHY,
        UNAVAILABLE,
        MISCONFIGURABLE
    }

    /**
     * Detailed health status record.
     */
    public record HealthDetail(HealthLevel level, String detail) {
        public boolean healthy() {
            return level == HealthLevel.HEALTHY;
        }
    }
}
