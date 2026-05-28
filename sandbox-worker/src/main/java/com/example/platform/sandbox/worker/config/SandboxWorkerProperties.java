package com.example.platform.sandbox.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the sandbox worker.
 */
@ConfigurationProperties(prefix = "sandbox.worker")
public record SandboxWorkerProperties(
    int maxExecutionSeconds,
    int maxOutputBytes,
    int maxCodeBytes,
    long timeoutBufferMs,
    List<String> allowedLanguages
) {
    public SandboxWorkerProperties {
        if (maxExecutionSeconds <= 0) maxExecutionSeconds = 5;
        if (maxExecutionSeconds > 120) maxExecutionSeconds = 120;
        if (maxOutputBytes <= 0) maxOutputBytes = 1024 * 1024;
        if (maxCodeBytes <= 0) maxCodeBytes = 65536;
        if (timeoutBufferMs <= 0) timeoutBufferMs = 500;
        if (allowedLanguages == null || allowedLanguages.isEmpty()) {
            allowedLanguages = List.of("python", "py");
        }
    }
}
