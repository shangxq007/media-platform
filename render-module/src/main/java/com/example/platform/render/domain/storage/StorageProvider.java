package com.example.platform.render.domain.storage;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for storage providers — abstracts object storage (Local, MinIO, S3, etc.).
 * StorageRuntime delegates to providers. Providers never access Product Runtime directly.
 */
public interface StorageProvider {
    String providerId();
    String providerType();

    boolean store(String storageReferenceId, byte[] data, Map<String, String> metadata);
    Optional<byte[]> fetch(String storageReferenceId);
    boolean delete(String storageReferenceId);
    boolean exists(String storageReferenceId);
    Map<String, Object> metadata(String storageReferenceId);
}
