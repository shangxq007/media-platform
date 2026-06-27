package com.example.platform.render.infrastructure.storage;

import com.example.platform.render.domain.storage.StorageProvider;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * S3 storage provider — architecture validation stub.
 * Validates that StorageProvider SPI supports Amazon S3 without kernel changes.
 */
@Component
public class S3StorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);

    @Override public String providerId() { return "s3"; }
    @Override public String providerType() { return "s3"; }

    @Override public boolean store(String id, byte[] data, Map<String, String> md) {
        log.info("S3: store {} ({} bytes)", id, data.length);
        return true;
    }

    @Override public Optional<byte[]> fetch(String id) {
        log.info("S3: fetch {}", id);
        return Optional.empty(); // stub
    }

    @Override public boolean delete(String id) {
        log.info("S3: delete {}", id);
        return true;
    }

    @Override public boolean exists(String id) {
        return true; // stub
    }

    @Override public Map<String, Object> metadata(String id) {
        return Map.of("provider", "s3", "exists", true);
    }
}
