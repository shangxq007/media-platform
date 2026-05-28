package com.example.platform.sandbox.app;

import java.util.Map;

/**
 * Request sent to an external sandbox worker.
 *
 * <p>Does NOT include sensitive data like authorization headers or full request bodies.
 */
public record SandboxWorkerRequest(
    String language,
    String code,
    long timeoutMs,
    int maxOutputBytes,
    Map<String, String> metadata
) {
    public SandboxWorkerRequest {
        if (language == null) language = "unknown";
        if (code == null) code = "";
        if (timeoutMs <= 0) timeoutMs = 5_000;
        if (maxOutputBytes <= 0) maxOutputBytes = 1024 * 1024;
        if (metadata == null) metadata = Map.of();
    }
}
