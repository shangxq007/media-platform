package com.example.platform.extension.domain;

/**
 * Declarative invocation contract (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 is declarative only: the descriptor declares invocation properties but
 * introduces NO new dispatch path. Generic execution routing remains
 * unchanged. The first provider declares SYNC_ONLY with a bounded 60s timeout
 * (matching {@code ToolSandboxPolicy.defaults()}), platform-owned retry,
 * cancellation and error model.</p>
 *
 * @param synchronous            true for the P1 first provider (SYNC_ONLY)
 * @param idempotency            NOT_DECLARED unless asserted by test evidence
 * @param cancelable             false (P1 declaration)
 * @param streaming              false (P1 declaration)
 * @param timeoutClassification  BOUNDED_DEFAULT_60S (matches ToolSandboxPolicy.defaults())
 * @param retryOwnership         PLATFORM (retry remains with callers)
 * @param progressReporting      false (P1 declaration)
 * @param errorBoundary          PLATFORM_ERROR_MODEL (ExtensionExecutionException-style codes)
 */
public record InvocationContract(
        boolean synchronous,
        Idempotency idempotency,
        boolean cancelable,
        boolean streaming,
        TimeoutClassification timeoutClassification,
        RetryOwnership retryOwnership,
        boolean progressReporting,
        ErrorBoundary errorBoundary) {

    /** Idempotency declaration; P1 asserts nothing without test evidence. */
    public enum Idempotency {
        NOT_DECLARED,
        TRUE,
        FALSE
    }

    /** Timeout classification. */
    public enum TimeoutClassification {
        BOUNDED_DEFAULT_60S
    }

    /** Retry ownership. */
    public enum RetryOwnership {
        PLATFORM
    }

    /** Error model ownership. */
    public enum ErrorBoundary {
        PLATFORM_ERROR_MODEL
    }

    public InvocationContract {
        if (idempotency == null) {
            throw new NullPointerException("idempotency must not be null");
        }
        if (timeoutClassification == null) {
            throw new NullPointerException("timeoutClassification must not be null");
        }
        if (retryOwnership == null) {
            throw new NullPointerException("retryOwnership must not be null");
        }
        if (errorBoundary == null) {
            throw new NullPointerException("errorBoundary must not be null");
        }
    }

    /**
     * Convenience factory for the frozen first-provider declaration:
     * SYNC_ONLY, bounded 60s, platform retry, platform error model.
     */
    public static InvocationContract syncOnlyDefault() {
        return new InvocationContract(
                true,
                Idempotency.NOT_DECLARED,
                false,
                false,
                TimeoutClassification.BOUNDED_DEFAULT_60S,
                RetryOwnership.PLATFORM,
                false,
                ErrorBoundary.PLATFORM_ERROR_MODEL);
    }
}
