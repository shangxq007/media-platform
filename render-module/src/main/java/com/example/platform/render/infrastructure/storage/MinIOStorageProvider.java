package com.example.platform.render.infrastructure.storage;

import com.example.platform.render.domain.storage.StorageProvider;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MinIO storage provider — architecture validation stub.
 * Validates that StorageProvider SPI supports MinIO without kernel changes.
 */
@Component
public class MinIOStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(MinIOStorageProvider.class);

    @Override public String providerId() { return "minio"; }
    @Override public String providerType() { return "minio"; }

    @Override public boolean store(String id, byte[] data, Map<String, String> md) {
        log.info("MinIO: store {} ({} bytes)", id, data.length);
        return true;
    }

    @Override public Optional<byte[]> fetch(String id) {
        log.info("MinIO: fetch {}", id);
        return Optional.empty(); // stub
    }

    @Override public boolean delete(String id) {
        log.info("MinIO: delete {}", id);
        return true;
    }

    @Override public boolean exists(String id) {
        return true; // stub
    }

    @Override public Map<String, Object> metadata(String id) {
        return Map.of("provider", "minio", "exists", true);
    }
}
