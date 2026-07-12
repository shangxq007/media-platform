package com.example.platform.outbox.subscription;

/**
 * Reference to retry policy configuration.
 */
public record RetryPolicyRef(String id, RetryPolicyDescriptor descriptor) {
    public RetryPolicyRef {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
    }

    public static RetryPolicyRef of(String id) {
        return new RetryPolicyRef(id, null);
    }
}
