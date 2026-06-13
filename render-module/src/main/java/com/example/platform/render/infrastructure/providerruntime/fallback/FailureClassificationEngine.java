package com.example.platform.render.infrastructure.providerruntime.fallback;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Classifies failures to determine retry and fallback behavior.
 */
@Component
public class FailureClassificationEngine {

    /**
     * Classify a failure and determine the appropriate action.
     */
    public FailureClassification classify(String providerName, Exception error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        // Timeout errors - should retry with same provider
        if (isTimeoutError(errorMessage)) {
            return new FailureClassification(
                    FailureType.TIMEOUT,
                    RetryPolicy.RETRY_SAME_PROVIDER,
                    true,
                    "Timeout error - retrying with same provider"
            );
        }

        // Resource errors - should fallback
        if (isResourceError(errorMessage)) {
            return new FailureClassification(
                    FailureType.RESOURCE_EXHAUSTION,
                    RetryPolicy.FALLBACK_TO_NEXT,
                    true,
                    "Resource exhaustion - falling back to next provider"
            );
        }

        // Configuration errors - should not retry
        if (isConfigurationError(errorMessage)) {
            return new FailureClassification(
                    FailureType.CONFIGURATION_ERROR,
                    RetryPolicy.NO_RETRY,
                    false,
                    "Configuration error - not retrying"
            );
        }

        // Capability errors - should fallback
        if (isCapabilityError(errorMessage)) {
            return new FailureClassification(
                    FailureType.CAPABILITY_MISMATCH,
                    RetryPolicy.FALLBACK_TO_NEXT,
                    true,
                    "Capability mismatch - falling back to next provider"
            );
        }

        // Unknown errors - retry once then fallback
        return new FailureClassification(
                FailureType.UNKNOWN,
                RetryPolicy.RETRY_ONCE_THEN_FALLBACK,
                true,
                "Unknown error - retrying once then falling back"
        );
    }

    private boolean isTimeoutError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("deadline exceeded");
    }

    private boolean isResourceError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("out of memory")
                || lower.contains("disk full")
                || lower.contains("no space")
                || lower.contains("resource exhausted")
                || lower.contains("too many open files");
    }

    private boolean isConfigurationError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("not found")
                || lower.contains("not configured")
                || lower.contains("missing")
                || lower.contains("invalid configuration");
    }

    private boolean isCapabilityError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("not supported")
                || lower.contains("unsupported")
                || lower.contains("capability")
                || lower.contains("format not supported");
    }

    /**
     * Types of failures.
     */
    public enum FailureType {
        TIMEOUT,
        RESOURCE_EXHAUSTION,
        CONFIGURATION_ERROR,
        CAPABILITY_MISMATCH,
        UNKNOWN
    }

    /**
     * Retry policies.
     */
    public enum RetryPolicy {
        NO_RETRY,
        RETRY_SAME_PROVIDER,
        RETRY_ONCE_THEN_FALLBACK,
        FALLBACK_TO_NEXT
    }

    /**
     * Failure classification result.
     */
    public record FailureClassification(
            FailureType failureType,
            RetryPolicy retryPolicy,
            boolean shouldRetry,
            String reason
    ) {}
}
