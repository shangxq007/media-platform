package com.example.platform.render.app.storage;

import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.storage.infrastructure.S3ObjectMaterializer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    public StorageRuntimeService(StorageReferenceRepository repo,
                                  ObjectProvider<S3ObjectMaterializer> s3MaterializerProvider) {
        this.repo = repo;
        this.s3Materializer = s3MaterializerProvider.getIfAvailable();
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
     * @param storageReferenceId the StorageReference identifier
     * @return the local file path, or empty if materialization fails
     */
    public Optional<String> materialize(String storageReferenceId) {
        var ref = repo.findById(storageReferenceId);
        if (ref.isEmpty()) {
            log.warn("Storage reference not found: {}", storageReferenceId);
            return Optional.empty();
        }

        var storageRef = ref.get();
        if (storageRef.provider() == null) {
            log.warn("Storage reference has no provider: {}", storageReferenceId);
            return Optional.empty();
        }

        // Try S3 materialization if available
        if (s3Materializer != null && storageRef.provider().name().startsWith("S3")) {
            return s3Materializer.materialize(
                storageRef.rootPath(),
                storageRef.relativePath(),
                storageRef.checksum()
            ).map(result -> result.localPath().toString());
        }

        // Fall back to local file verification
        var localPath = java.nio.file.Path.of(storageRef.absolutePath());
        if (java.nio.file.Files.exists(localPath)) {
            return Optional.of(localPath.toString());
        }

        log.warn("Local file does not exist: {}", localPath);
        return Optional.empty();
    }
}
