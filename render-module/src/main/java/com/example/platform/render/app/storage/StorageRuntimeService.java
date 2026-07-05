package com.example.platform.render.app.storage;

import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.storage.infrastructure.S3ObjectMaterializer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central service for StorageReference management and materialization.
 *
 * <p>Materialization supports both local and S3-compatible storage:
 * <ul>
 *   <li>LOCAL: verifies file exists on filesystem, returns absolute path</li>
 *   <li>S3-compatible: downloads object to temp file via S3ObjectMaterializer</li>
 * </ul>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>No signed URLs persisted or exposed</li>
 *   <li>No bucket/key exposed in public API</li>
 *   <li>Local paths only returned to internal runtime code</li>
 * </ul>
 */
@Service
public class StorageRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(StorageRuntimeService.class);
    private final StorageReferenceRepository repo;
    private final S3ObjectMaterializer s3Materializer;

    @Autowired
    public StorageRuntimeService(StorageReferenceRepository repo) {
        this(repo, null);
    }

    public StorageRuntimeService(StorageReferenceRepository repo,
                                  S3ObjectMaterializer s3Materializer) {
        this.repo = repo;
        this.s3Materializer = s3Materializer;
    }

    @Transactional
    public StorageReference register(StorageReference ref) {
        var saved = repo.save(ref);
        log.info("Storage reference registered: id={} path={}", saved.storageReferenceId(), saved.absolutePath());
        return saved;
    }

    /**
     * Materialize a StorageReference to a local file path.
     *
     * <p>For LOCAL provider: verifies file exists on filesystem.
     * For S3-compatible providers (S3, S3_COMPATIBLE, OBJECT_STORAGE): downloads
     * object to a local temp file via S3ObjectMaterializer.</p>
     *
     * @param storageReferenceId the StorageReference identifier
     * @return the local file path
     * @throws IllegalArgumentException if StorageReference not found
     * @throws IllegalStateException if materialization fails
     */
    public String materialize(String storageReferenceId) {
        var ref = repo.findById(storageReferenceId)
                .orElseThrow(() -> new IllegalArgumentException("Storage not found: " + storageReferenceId));

        String providerType = ref.providerType();
        if (isS3CompatibleProvider(providerType)) {
            return materializeS3(ref);
        }

        // Local provider: verify file exists
        String path = ref.absolutePath();
        var file = new java.io.File(path);
        if (!file.exists()) throw new IllegalStateException("File not materialized: " + path);
        return path;
    }

    /**
     * Verify checksum of a materialized StorageReference.
     *
     * <p>For LOCAL provider: reads file bytes and computes SHA-256.
     * For S3-compatible providers: if the temp file still exists from
     * materialization, verifies against it; otherwise re-downloads and verifies.</p>
     */
    public boolean verifyChecksum(String storageReferenceId) {
        var ref = repo.findById(storageReferenceId).orElseThrow();
        try {
            String providerType = ref.providerType();
            if (isS3CompatibleProvider(providerType) && s3Materializer != null) {
                return verifyS3Checksum(ref);
            }

            byte[] fileBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ref.absolutePath()));
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(ref.checksum());
        } catch (Exception e) { return false; }
    }

    public Optional<StorageReference> find(String id) { return repo.findById(id); }
    public boolean exists(String id) { return repo.exists(id); }
    @Transactional public void delete(String id) { repo.delete(id); }

    /**
     * Materialize an S3-compatible object to a local temp file.
     */
    private String materializeS3(StorageReference ref) {
        if (s3Materializer == null || !s3Materializer.isEnabled()) {
            throw new IllegalStateException(
                    "S3 materialization not available: S3ObjectMaterializer not enabled");
        }

        String bucket = ref.rootPath();
        String objectKey = ref.relativePath();

        var result = s3Materializer.materialize(bucket, objectKey, ref.checksum());
        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "S3 object not materialized: bucket=" + bucket + " key=" + objectKey);
        }

        var materialized = result.get();
        log.info("S3 object materialized: storageRef={} bucket={} key={} localSize={} checksum={}",
                ref.storageReferenceId(), bucket, objectKey, materialized.sizeBytes(),
                materialized.checksum());

        return materialized.localPath().toAbsolutePath().toString();
    }

    /**
     * Verify checksum for an S3-compatible StorageReference.
     */
    private boolean verifyS3Checksum(StorageReference ref) {
        // Re-materialize to verify
        String bucket = ref.rootPath();
        String objectKey = ref.relativePath();

        var result = s3Materializer.materialize(bucket, objectKey, ref.checksum());
        if (result.isEmpty()) return false;

        var materialized = result.get();
        // Clean up the temp file after verification
        try {
            java.nio.file.Files.deleteIfExists(materialized.localPath());
        } catch (java.io.IOException ignored) {}

        return materialized.checksum().equalsIgnoreCase(ref.checksum());
    }

    /**
     * Check if the provider type is an S3-compatible object storage.
     *
     * <p>Accepted values:
     * <ul>
     *   <li>{@code S3} — generic S3-compatible (preferred)</li>
     *   <li>{@code S3_COMPATIBLE} — explicit S3-compatible alias</li>
     *   <li>{@code OBJECT_STORAGE} — storage-neutral alias</li>
     * </ul>
     *
     * <p>Rejected values (not S3-compatible by default):
     * <ul>
     *   <li>{@code MINIO} — not a provider type; use S3</li>
     *   <li>{@code OSS}, {@code GCS}, {@code AZURE} — future native providers,
     *       not accepted as S3-compatible without explicit validation</li>
     *   <li>RustFS, SeaweedFS — deployment backends, not provider types</li>
     * </ul>
     */
    private static boolean isS3CompatibleProvider(String providerType) {
        if (providerType == null) return false;
        return switch (providerType.toUpperCase()) {
            case "S3", "S3_COMPATIBLE", "OBJECT_STORAGE" -> true;
            default -> false;
        };
    }
}
