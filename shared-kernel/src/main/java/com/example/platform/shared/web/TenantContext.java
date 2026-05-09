package com.example.platform.shared.web;

/**
 * ThreadLocal-based tenant context for multi-tenant isolation.
 * <p>
 * The tenantId is set by {@code ApiKeyAuthFilter} after successful API Key
 * authentication and cleared after the request completes. All downstream
 * services read from this context to enforce tenant-scoped data access.
 * </p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Set the current tenant id for this thread.
     */
    public static void set(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Get the current tenant id for this thread, or {@code null} if not set.
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clear the current tenant id. Must be called in a {@code finally} block
     * to prevent thread-pool leakage.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
