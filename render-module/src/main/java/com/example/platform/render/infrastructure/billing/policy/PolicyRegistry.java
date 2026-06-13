package com.example.platform.render.infrastructure.billing.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing billing policies.
 * Provides a central location for policy storage and retrieval.
 */
@Service
public class PolicyRegistry {

    private static final Logger log = LoggerFactory.getLogger(PolicyRegistry.class);

    private final Map<String, Policy> policies = new ConcurrentHashMap<>();

    /**
     * Register a policy.
     */
    public void register(Policy policy) {
        policies.put(policy.id(), policy);
        log.info("Registered policy: {} ({}) - {}", policy.id(), policy.name(), 
                policy.status());
    }

    /**
     * Get a policy by ID.
     */
    public Policy get(String policyId) {
        return policies.get(policyId);
    }

    /**
     * List all policies.
     */
    public List<Policy> listAll() {
        return List.copyOf(policies.values());
    }

    /**
     * List active policies.
     */
    public List<Policy> listActive() {
        return policies.values().stream()
                .filter(Policy::isActive)
                .toList();
    }

    /**
     * List policies by scope.
     */
    public List<Policy> listByScope(PolicyScope scope) {
        return policies.values().stream()
                .filter(p -> p.scope() == scope)
                .toList();
    }

    /**
     * List policies by scope and scope ID.
     */
    public List<Policy> listByScope(PolicyScope scope, String scopeId) {
        return policies.values().stream()
                .filter(p -> p.scope() == scope && scopeId.equals(p.scopeId()))
                .toList();
    }

    /**
     * Remove a policy.
     */
    public void remove(String policyId) {
        policies.remove(policyId);
        log.info("Removed policy: {}", policyId);
    }

    /**
     * Check if a policy exists.
     */
    public boolean exists(String policyId) {
        return policies.containsKey(policyId);
    }

    /**
     * Get the count of registered policies.
     */
    public int count() {
        return policies.size();
    }

    /**
     * Clear all policies (for testing).
     */
    public void clear() {
        policies.clear();
    }

    /**
     * Register default policies.
     */
    public void registerDefaults() {
        // Free tier: basic limits
        register(Policy.forTenant(
                "policy-free-tier",
                "Free Tier Limits",
                "default",
                100,
                List.of(
                        PolicyCondition.equals(PolicyCondition.ConditionType.TIER, "tier", "FREE")
                ),
                List.of(
                        PolicyAction.overrideQuota("render", 50, "Free tier: 50 renders per month"),
                        PolicyAction.applyDiscount(0, "Free tier: no discount")
                )
        ));

        // Pro tier: higher limits, discount
        register(Policy.forTenant(
                "policy-pro-tier",
                "Pro Tier Benefits",
                "default",
                100,
                List.of(
                        PolicyCondition.equals(PolicyCondition.ConditionType.TIER, "tier", "PRO")
                ),
                List.of(
                        PolicyAction.overrideQuota("render", 500, "Pro tier: 500 renders per month"),
                        PolicyAction.applyDiscount(20, "Pro tier: 20% discount")
                )
        ));

        // GPU restriction for free tier
        register(Policy.global(
                "policy-gpu-free-restriction",
                "GPU Restriction for Free Tier",
                200,
                List.of(
                        PolicyCondition.equals(PolicyCondition.ConditionType.TIER, "tier", "FREE"),
                        PolicyCondition.equals(PolicyCondition.ConditionType.GPU_ENABLED, "gpu", true)
                ),
                List.of(
                        PolicyAction.requireUpgrade("STARTER", 
                                "GPU rendering requires Starter tier or higher")
                )
        ));

        log.info("Registered {} default policies", policies.size());
    }
}
