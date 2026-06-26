package com.example.platform.render.domain.execution;

import java.util.Map;

/**
 * Key/value execution hints — future extensibility.
 * Examples: preferredBackend, preferGPU, cacheAllowed, temporaryWorkspace.
 */
public record ExecutionHint(String key, String value) {
    public static Map<String, String> toMap(ExecutionHint... hints) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (var h : hints) m.put(h.key(), h.value());
        return m;
    }
}
