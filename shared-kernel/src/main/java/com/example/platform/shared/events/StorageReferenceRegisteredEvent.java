package com.example.platform.shared.events;

/**
 * Published when a storage reference is registered.
 */
public record StorageReferenceRegisteredEvent(
        String storageReferenceId,
        String providerType,
        String absolutePath,
        long fileSize) {}
