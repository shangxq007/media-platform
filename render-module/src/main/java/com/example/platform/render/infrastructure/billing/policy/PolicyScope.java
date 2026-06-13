package com.example.platform.render.infrastructure.billing.policy;

/**
 * Policy scope levels.
 */
public enum PolicyScope {
    /**
     * Applies to all tenants and workspaces.
     */
    GLOBAL,

    /**
     * Applies to a specific tenant.
     */
    TENANT,

    /**
     * Applies to a specific workspace.
     */
    WORKSPACE
}
