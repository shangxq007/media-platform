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
        if (path.contains("..")) {
            throw new IllegalArgumentException("path template produced invalid path");
        }
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
}
