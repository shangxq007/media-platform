package com.example.platform.outbox.subscription;

public record RetryPolicyDescriptor(
    int maxAttempts,
    int initialDelaySeconds,
    int maxDelaySeconds,
    double backoffMultiplier,
    boolean jitterEnabled
) {
    public static RetryPolicyDescriptor defaultPolicy() {
        return new RetryPolicyDescriptor(3, 1, 60, 2.0, true);
    }
}
