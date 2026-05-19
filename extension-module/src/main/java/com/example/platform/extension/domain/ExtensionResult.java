package com.example.platform.extension.domain;

import java.util.HashMap;
import java.util.Map;

public record ExtensionResult(
        boolean success,
        String outputJson,
        String errorCode,
        String errorMessage,
        Map<String, Object> metrics
) {
    public ExtensionResult {
        outputJson = outputJson != null ? outputJson : "{}";
        metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
    }

    public static ExtensionResult success(String outputJson) {
        return new ExtensionResult(true, outputJson, null, null, Map.of());
    }

    public static ExtensionResult success(String outputJson, Map<String, Object> metrics) {
        return new ExtensionResult(true, outputJson, null, null, metrics);
    }

    public static ExtensionResult failure(String errorCode, String errorMessage) {
        return new ExtensionResult(false, "{}", errorCode, errorMessage, Map.of());
    }

    public static ExtensionResult failure(String errorCode, String errorMessage, Map<String, Object> metrics) {
        return new ExtensionResult(false, "{}", errorCode, errorMessage, metrics);
    }

    public ExtensionResult withMetric(String key, Object value) {
        Map<String, Object> merged = new HashMap<>(metrics);
        merged.put(key, value);
        return new ExtensionResult(success, outputJson, errorCode, errorMessage, merged);
    }
}
