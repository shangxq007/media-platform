package com.example.platform.storage.api;

/** Storage-owned object ownership; placement namespace is never an ownership source. */
public record StorageOwnershipScope(String tenantId, String projectId) {

    public StorageOwnershipScope {
        requireText(tenantId, "tenantId");
        if (projectId != null && projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must be null or non-blank");
        }
    }

    public static StorageOwnershipScope tenant(String tenantId) {
        return new StorageOwnershipScope(tenantId, null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
