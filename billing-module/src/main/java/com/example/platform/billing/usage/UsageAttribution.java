package com.example.platform.billing.usage;

import java.util.Objects;

/**
 * Attribution for a usage observation.
 *
 * <p>This is a separate record from {@link UsageRecord} for typing clarity. Tenant is the
 * only required field.</p>
 *
 * @param tenantId    the tenant (required, non-blank)
 * @param projectId   the project (nullable)
 * @param actorRef    bounded actor snapshot (nullable)
 * @param operationRef operation reference (nullable)
 * @param executionRef execution reference (nullable, future-compatible)
 * @param providerRef provider reference (nullable)
 * @param capability  capability (nullable)
 */
public record UsageAttribution(
        String tenantId,
        String projectId,
        CanonicalActorRef actorRef,
        String operationRef,
        String executionRef,
        String providerRef,
        String capability) {

    public UsageAttribution {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }
}
