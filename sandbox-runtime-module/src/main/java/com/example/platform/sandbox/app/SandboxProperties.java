package com.example.platform.sandbox.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for the sandbox runtime module.
 *
 * <p><strong>Security defaults:</strong> All execution capabilities are disabled by default.
 * Production environments must NOT enable in-process eval — use external sandbox workers instead.
 */
@ConfigurationProperties(prefix = "sandbox")
public record SandboxProperties(
    /**
     * Master switch for the sandbox module. When false, all execute requests are rejected.
     */
    boolean enabled,

    /**
     * Execution mode: disabled, external, or in-process.
     * Production MUST use 'external' or 'disabled'. Never 'in-process'.
     */
    SandboxExecutionMode executionMode,

    /**
     * Whether to allow in-process ScriptEngine.eval() execution.
     * MUST be false in production — blocklist is bypassable, same-JVM execution is unsafe.
     */
    boolean allowInProcessEval,

    /**
     * Languages allowed for execution. Empty list means no languages allowed.
     * Example: groovy,javascript,js,python,py,wasm
     */
    List<String> allowedLanguages,

    /**
     * Maximum execution time in seconds. Clamped to [1, 120].
     */
    int maxExecutionSeconds,

    /**
     * Maximum output size in bytes. Default 1MB.
     */
    int maxOutputBytes,

    /**
     * Worker configuration for external execution mode.
     */
    WorkerProperties worker
) {
    public SandboxProperties {
        if (executionMode == null) executionMode = SandboxExecutionMode.DISABLED;
        if (allowedLanguages == null) allowedLanguages = List.of();
        if (maxExecutionSeconds <= 0) maxExecutionSeconds = 5;
        if (maxExecutionSeconds > 120) maxExecutionSeconds = 120;
        if (maxOutputBytes <= 0) maxOutputBytes = 1024 * 1024;
        if (worker == null) worker = WorkerProperties.defaults();
    }

    public static SandboxProperties defaults() {
        return new SandboxProperties(false, SandboxExecutionMode.DISABLED, false,
                List.of(), 5, 1024 * 1024, WorkerProperties.defaults());
    }

    /**
     * Worker connection configuration for external execution mode.
     */
    public record WorkerProperties(
        /**
         * Base URL of the sandbox worker service.
         * Example: http://sandbox-worker:8091
         */
        String baseUrl,

        /**
         * HTTP connect timeout in milliseconds.
         */
        int connectTimeoutMs,

        /**
         * HTTP read timeout in milliseconds.
         */
        int readTimeoutMs
    ) {
        public WorkerProperties {
            if (baseUrl == null) baseUrl = "";
            if (connectTimeoutMs <= 0) connectTimeoutMs = 1_000;
            if (readTimeoutMs <= 0) readTimeoutMs = 5_000;
        }

        public static WorkerProperties defaults() {
            return new WorkerProperties("", 1_000, 5_000);
        }
    }
}
