package com.example.platform.shared.tenant;

import java.nio.file.Path;
import java.util.Set;

public final class StorageKeyPolicy {

    private StorageKeyPolicy() {}

    public static final String SEPARATOR = "/";

    public static String tenantPath(String tenantId, String workspaceId, String projectId, String... segments) {
        assertValidId(tenantId, "tenantId");
        assertValidId(projectId, "projectId");

        StringBuilder sb = new StringBuilder();
        sb.append("tenant").append(SEPARATOR).append(sanitize(tenantId));
        sb.append(SEPARATOR).append("workspace").append(SEPARATOR).append(
                workspaceId != null && !workspaceId.isBlank() ? sanitize(workspaceId) : "default");
        sb.append(SEPARATOR).append("project").append(SEPARATOR).append(sanitize(projectId));

        for (String seg : segments) {
            if (seg != null && !seg.isBlank()) {
                sb.append(SEPARATOR).append(sanitize(seg));
            }
        }
        return sb.toString();
    }

    public static String assetPath(String tenantId, String workspaceId, String projectId,
                                    String assetId, String filename) {
        return tenantPath(tenantId, workspaceId, projectId, "assets", assetId, filename);
    }

    public static String exportPath(String tenantId, String workspaceId, String projectId,
                                     String exportId, String filename) {
        return tenantPath(tenantId, workspaceId, projectId, "exports", exportId, filename);
    }

    public static String tempPath(String tenantId, String workspaceId, String filename) {
        assertValidId(tenantId, "tenantId");
        StringBuilder sb = new StringBuilder();
        sb.append("tenant").append(SEPARATOR).append(sanitize(tenantId));
        sb.append(SEPARATOR).append("workspace").append(SEPARATOR).append(
                workspaceId != null && !workspaceId.isBlank() ? sanitize(workspaceId) : "default");
        sb.append(SEPARATOR).append("tmp");
        sb.append(SEPARATOR).append(sanitize(filename));
        return sb.toString();
    }

    public static void assertValidPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Storage path cannot be null");
        }
        if (path.contains("..")) {
            throw new SecurityException("Path traversal detected: " + path);
        }
        if (path.contains("\\")) {
            throw new SecurityException("Backslash not allowed in storage paths: " + path);
        }
        if (path.startsWith("/")) {
            throw new SecurityException("Absolute paths not allowed: " + path);
        }
    }

    public static boolean hasTenantPrefix(String path, String tenantId) {
        if (path == null || tenantId == null) return false;
        return path.startsWith("tenant/" + sanitize(tenantId) + "/");
    }

    public static String extractTenantFromPath(String path) {
        if (path == null || !path.startsWith("tenant/")) return null;
        String[] parts = path.split(SEPARATOR, 3);
        return parts.length >= 2 ? parts[1] : null;
    }

    private static void assertValidId(String id, String fieldName) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new SecurityException("Invalid " + fieldName + ": contains path traversal characters");
        }
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }
}
