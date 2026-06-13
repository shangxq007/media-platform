package com.example.platform.render.infrastructure.providerruntime.fallback;

import com.example.platform.render.infrastructure.RenderProvider;

import java.util.List;
import java.util.Map;

/**
 * Result of a fallback execution attempt.
 */
public record FallbackExecutionResult(
        boolean success,
        String successfulProvider,
        RenderProvider.RenderResult result,
        List<String> executionPath,
        Map<String, String> failureReasons,
        String finalError
) {
    /**
     * Get the number of providers attempted.
     */
    public int providersAttempted() {
        return executionPath.size();
    }

    /**
     * Check if fallback was used.
     */
    public boolean usedFallback() {
        return executionPath.size() > 1;
    }

    /**
     * Get a summary of the execution.
     */
    public String getSummary() {
        if (success) {
            return "Succeeded with " + successfulProvider
                    + (usedFallback() ? " (after " + (executionPath.size() - 1) + " fallbacks)" : "");
        }
        return "Failed after " + executionPath.size() + " attempts: " + finalError;
    }
}
