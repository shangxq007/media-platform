package com.example.platform.storage.infrastructure.experimental.opendal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenDAL experimental materializer for non-S3 backends (local fs POC).
 * Disabled by default. Not for production use.
 * 
 * Current scope: local filesystem write/read/stat only.
 * Does NOT replace S3ObjectMaterializer or S3BlobStorageProvider.
 */
@Component
@ConditionalOnProperty(prefix = "storage.experimental.opendal", name = "enabled", havingValue = "true")
public class OpenDalMaterializer {

    private static final Logger log = LoggerFactory.getLogger(OpenDalMaterializer.class);
    private final OpenDalExperimentalProperties properties;

    public OpenDalMaterializer(OpenDalExperimentalProperties properties) {
        this.properties = properties;
        log.info("OpenDalMaterializer initialized: backend={} root={} mode={} (EXPERIMENTAL - not for production)",
                properties.getBackend(), properties.getRoot(), properties.getMode());
    }

    /**
     * Write object to local filesystem (POC only).
     */
    public boolean write(String objectKey, byte[] data) {
        try {
            Path target = Path.of(properties.getRoot(), objectKey);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.debug("OpenDAL write: key={} bytes={}", objectKey, data.length);
            return true;
        } catch (IOException e) {
            log.warn("OpenDAL write failed: key={} error={}", objectKey, e.getMessage());
            return false;
        }
    }

    /**
     * Read object from local filesystem (POC only).
     */
    public Optional<byte[]> read(String objectKey) {
        try {
            Path source = Path.of(properties.getRoot(), objectKey);
            if (!Files.exists(source)) {
                return Optional.empty();
            }
            byte[] data = Files.readAllBytes(source);
            log.debug("OpenDAL read: key={} bytes={}", objectKey, data.length);
            return Optional.of(data);
        } catch (IOException e) {
            log.warn("OpenDAL read failed: key={} error={}", objectKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if object exists (POC only).
     */
    public boolean exists(String objectKey) {
        Path source = Path.of(properties.getRoot(), objectKey);
        return Files.exists(source);
    }

    /**
     * Get object size (POC only).
     */
    public Optional<Long> size(String objectKey) {
        try {
            Path source = Path.of(properties.getRoot(), objectKey);
            if (!Files.exists(source)) {
                return Optional.empty();
            }
            return Optional.of(Files.size(source));
        } catch (IOException e) {
            log.warn("OpenDAL size failed: key={} error={}", objectKey, e.getMessage());
            return Optional.empty();
        }
    }
}
