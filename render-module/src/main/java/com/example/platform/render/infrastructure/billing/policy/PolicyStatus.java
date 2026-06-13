package com.example.platform.render.infrastructure.billing.policy;

/**
 * Policy lifecycle status.
 */
public enum PolicyStatus {
    /**
     * Policy is active and will be evaluated.
     */
    ACTIVE,

    /**
     * Policy is inactive and will be skipped.
     */
    INACTIVE,

    /**
     * Policy is in draft mode (for testing).
     */
    DRAFT,

    /**
     * Policy has expired.
     */
    EXPIRED
}
