package com.example.platform.delivery.app;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DeliveryPathRenderer {

    private DeliveryPathRenderer() {}

    public static String render(String template, Map<String, String> vars) {
        String path = template != null ? template : "{tenantId}/{projectId}/{jobId}/output.mp4";
        for (Map.Entry<String, String> e : vars.entrySet()) {
            path = path.replace("{" + e.getKey() + "}", sanitize(e.getValue()));
        }
        assertValidRenderedPath(path);
        return path;
    }

    public static Map<String, String> vars(String tenantId, String projectId, String renderJobId, String filename) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tenantId", tenantId);
        m.put("projectId", projectId);
        m.put("jobId", renderJobId);
        m.put("filename", filename);
        return m;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Validates a rendered path for traversal attacks.
     * Defense: reject "..", absolute paths, backslashes, percent-encoded traversal, null bytes.
     */
    static void assertValidRenderedPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Rendered path cannot be null");
        }
        // Reject null bytes
        if (path.contains("\0")) {
            throw new IllegalArgumentException("Rendered path contains null byte");
        }
        // Reject backslash
        if (path.contains("\\")) {
            throw new IllegalArgumentException("Rendered path contains backslash");
        }
        // Reject absolute paths
        if (path.startsWith("/")) {
            throw new IllegalArgumentException("Rendered path is absolute");
        }
        // Reject ".." segments
        String lower = path.toLowerCase();
        if (lower.contains("..")) {
            throw new IllegalArgumentException("path template produced invalid path: traversal detected");
        }
        // Reject percent-encoded traversal
        if (lower.contains("%2e%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%252e") || lower.contains("%252f") || lower.contains("%255c")) {
            throw new IllegalArgumentException("path template produced invalid path: encoded traversal detected");
        }
        // Segment-level validation
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("path template produced invalid path: dot segment");
            }
        }
    }
}
