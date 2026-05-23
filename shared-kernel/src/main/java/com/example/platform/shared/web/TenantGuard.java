package com.example.platform.shared.web;

import java.util.Map;

/**
 * Enforces that data access uses the tenant established in {@link TenantContext}.
 */
public final class TenantGuard {

    private static final ErrorCode TENANT_REQUIRED = new ConfigurableErrorCode(
            "COMMON-401-002", 401002,
            Map.of("en", "Tenant context is required", "zh", "需要租户上下文"),
            "common", 401);

    private static final ErrorCode TENANT_ACCESS_DENIED = new ConfigurableErrorCode(
            "COMMON-403-002", 403002,
            Map.of("en", "Cross-tenant access is not allowed", "zh", "不允许跨租户访问"),
            "common", 403);

    private TenantGuard() {
    }

    public static String requireTenantId() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new PlatformException(TENANT_REQUIRED);
        }
        return tenantId;
    }

    public static void assertSameTenant(String resourceTenantId) {
        String current = requireTenantId();
        if (resourceTenantId == null || !current.equals(resourceTenantId)) {
            throw new PlatformException(TENANT_ACCESS_DENIED);
        }
    }

    public static String tenantOrDefault(String explicitTenantId) {
        if (explicitTenantId != null && !explicitTenantId.isBlank()) {
            assertSameTenantIfContextPresent(explicitTenantId);
            return explicitTenantId;
        }
        return requireTenantId();
    }

    /** Validates tenant match only when {@link TenantContext} is already established (HTTP/worker). */
    public static void assertSameTenantIfContextPresent(String resourceTenantId) {
        String current = TenantContext.get();
        if (current != null && !current.isBlank()) {
            assertSameTenant(resourceTenantId);
        }
    }
}
