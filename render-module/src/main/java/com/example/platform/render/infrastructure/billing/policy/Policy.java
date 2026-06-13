package com.example.platform.render.infrastructure.billing.policy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Declarative policy that defines billing rules.
 * Policies are data-driven and can be added without code changes.
 */
public record Policy(
        String id,
        String name,
        String description,
        int priority,
        PolicyScope scope,
        String scopeId,       // tenantId, workspaceId, or null for global
        PolicyStatus status,
        List<PolicyCondition> conditions,
        List<PolicyAction> actions,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Map<String, Object> metadata
) {
    /**
     * Create a new policy.
     */
    public static Policy create(String id, String name, String description, int priority,
                                 PolicyScope scope, String scopeId,
                                 List<PolicyCondition> conditions,
                                 List<PolicyAction> actions) {
        Instant now = Instant.now();
        return new Policy(id, name, description, priority, scope, scopeId,
                PolicyStatus.ACTIVE, conditions, actions, now, null, now, Map.of());
    }

    /**
     * Create a global policy.
     */
    public static Policy global(String id, String name, int priority,
                                 List<PolicyCondition> conditions,
                                 List<PolicyAction> actions) {
        return create(id, name, "", priority, PolicyScope.GLOBAL, null, conditions, actions);
    }

    /**
     * Create a tenant-specific policy.
     */
    public static Policy forTenant(String id, String name, String tenantId, int priority,
                                    List<PolicyCondition> conditions,
                                    List<PolicyAction> actions) {
        return create(id, name, "", priority, PolicyScope.TENANT, tenantId, conditions, actions);
    }

    /**
     * Check if policy is active now.
     */
    public boolean isActive() {
        Instant now = Instant.now();
        return status == PolicyStatus.ACTIVE
                && (effectiveFrom == null || effectiveFrom.isBefore(now))
                && (effectiveTo == null || effectiveTo.isAfter(now));
    }

    /**
     * Check if policy applies to a specific scope.
     */
    public boolean appliesTo(PolicyScope scope, String scopeId) {
        if (this.scope == PolicyScope.GLOBAL) return true;
        if (this.scope == scope && this.scopeId.equals(scopeId)) return true;
        return false;
    }

    /**
     * Activate the policy.
     */
    public Policy activate() {
        return new Policy(id, name, description, priority, scope, scopeId,
                PolicyStatus.ACTIVE, conditions, actions, effectiveFrom, effectiveTo,
                createdAt, metadata);
    }

    /**
     * Deactivate the policy.
     */
    public Policy deactivate() {
        return new Policy(id, name, description, priority, scope, scopeId,
                PolicyStatus.INACTIVE, conditions, actions, effectiveFrom, effectiveTo,
                createdAt, metadata);
    }
}
