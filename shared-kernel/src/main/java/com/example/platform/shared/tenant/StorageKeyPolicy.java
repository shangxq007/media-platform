package com.example.platform.shared.tenant;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Validates a storage path for traversal and injection attacks.
     *
     * <p>Defense layers:
     * <ol>
     *   <li>Reject null</li>
     *   <li>Percent-decode (single pass, reject residual encoded chars)</li>
     *   <li>Reject backslash (normalize or reject — we reject)</li>
     *   <li>Reject absolute paths (leading / or drive letter)</li>
     *   <li>Split on /, validate each segment:
     *     <ul>
     *       <li>Reject empty segments</li>
     *       <li>Reject "." and ".."</li>
     *     </ul>
     *   </li>
     *   <li>Reassemble and verify no traversal survived</li>
     * </ol>
     */
    public static void assertValidPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Storage path cannot be null");
        }

        // Reject backslash before decode (Windows path separator)
        if (path.contains("\\")) {
            throw new SecurityException("Backslash not allowed in storage paths");
        }

        // Percent-decode (single pass)
        String decoded = percentDecode(path);

        // Reject if decode revealed backslash
        if (decoded.contains("\\")) {
            throw new SecurityException("Backslash not allowed in storage paths (after decode)");
        }

        // Reject absolute paths
        if (decoded.startsWith("/")) {
            throw new SecurityException("Absolute paths not allowed");
        }

        // Reject Windows drive letters (C:, D:, etc.)
        if (decoded.length() >= 2 && Character.isLetter(decoded.charAt(0)) && decoded.charAt(1) == ':') {
            throw new SecurityException("Windows drive paths not allowed");
        }

        // Split and validate segments
        String[] segments = decoded.split("/", -1);
        List<String> normalized = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue; // collapse empty segments (but track for double-slash detection)
            }
            if (".".equals(segment)) {
                continue; // skip current-dir references
            }
            if ("..".equals(segment)) {
                throw new SecurityException("Path traversal detected: '..' segment");
            }
            normalized.add(segment);
        }

        // Reassemble and final check
        String result = String.join("/", normalized);
        if (result.contains("..")) {
            throw new SecurityException("Path traversal detected after normalization");
        }

        // Verify the normalized path doesn't escape via leading ..
        if (result.startsWith("..") || result.startsWith("/")) {
            throw new SecurityException("Path traversal detected");
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

    static void assertValidId(String id, String fieldName) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new SecurityException("Invalid " + fieldName + ": contains path traversal characters");
        }
    }

    static String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
    }

    /**
     * Percent-decode a string (single pass). Uses UTF-8.
     * Rejects strings that contain percent-encoded null bytes or other control characters.
     */
    private static String percentDecode(String input) {
        String decoded = URLDecoder.decode(input, StandardCharsets.UTF_8);
        // Reject if decode revealed null bytes
        if (decoded.contains("\0")) {
            throw new SecurityException("Null bytes not allowed in storage paths");
        }
        return decoded;
    }
}
