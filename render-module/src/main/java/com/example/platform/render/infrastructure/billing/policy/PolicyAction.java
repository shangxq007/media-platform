package com.example.platform.render.infrastructure.billing.policy;

import java.util.Map;

/**
 * Action to take when a policy condition is met.
 * Actions define the outcome of policy evaluation.
 */
public record PolicyAction(
        ActionType type,
        Map<String, Object> parameters,
        String reason
) {
    /**
     * Create an ALLOW action.
     */
    public static PolicyAction allow(String reason) {
        return new PolicyAction(ActionType.ALLOW, Map.of(), reason);
    }

    /**
     * Create a DENY action.
     */
    public static PolicyAction deny(String reason) {
        return new PolicyAction(ActionType.DENY, Map.of(), reason);
    }

    /**
     * Create a THROTTLE action.
     */
    public static PolicyAction throttle(long retryAfterMs, String reason) {
        return new PolicyAction(ActionType.THROTTLE, Map.of("retryAfterMs", retryAfterMs), reason);
    }

    /**
     * Create an APPLY_DISCOUNT action.
     */
    public static PolicyAction applyDiscount(double discountPercent, String reason) {
        return new PolicyAction(ActionType.APPLY_DISCOUNT,
                Map.of("discountPercent", discountPercent), reason);
    }

    /**
     * Create a GRANT_CREDITS action.
     */
    public static PolicyAction grantCredits(double amount, String reason) {
        return new PolicyAction(ActionType.GRANT_CREDITS,
                Map.of("amount", amount), reason);
    }

    /**
     * Create a REQUIRE_UPGRADE action.
     */
    public static PolicyAction requireUpgrade(String requiredTier, String reason) {
        return new PolicyAction(ActionType.REQUIRE_UPGRADE,
                Map.of("requiredTier", requiredTier), reason);
    }

    /**
     * Create an OVERRIDE_QUOTA action.
     */
    public static PolicyAction overrideQuota(String quotaKey, long newLimit, String reason) {
        return new PolicyAction(ActionType.OVERRIDE_QUOTA,
                Map.of("quotaKey", quotaKey, "newLimit", newLimit), reason);
    }

    /**
     * Create an APPLY_MULTIPLIER action.
     */
    public static PolicyAction applyMultiplier(double multiplier, String reason) {
        return new PolicyAction(ActionType.APPLY_MULTIPLIER,
                Map.of("multiplier", multiplier), reason);
    }

    /**
     * Get a parameter value.
     */
    public Object getParam(String key) {
        return parameters.get(key);
    }

    /**
     * Get a parameter as double.
     */
    public double getDoubleParam(String key, double defaultValue) {
        Object val = parameters.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    /**
     * Get a parameter as long.
     */
    public long getLongParam(String key, long defaultValue) {
        Object val = parameters.get(key);
        if (val instanceof Number n) return n.longValue();
        return defaultValue;
    }

    /**
     * Get a parameter as string.
     */
    public String getStringParam(String key, String defaultValue) {
        Object val = parameters.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Action types.
     */
    public enum ActionType {
        /**
         * Allow the operation.
         */
        ALLOW,

        /**
         * Deny the operation.
         */
        DENY,

        /**
         * Throttle the operation (rate limiting).
         */
        THROTTLE,

        /**
         * Apply a discount to the cost.
         */
        APPLY_DISCOUNT,

        /**
         * Grant credits to the account.
         */
        GRANT_CREDITS,

        /**
         * Require a tier upgrade.
         */
        REQUIRE_UPGRADE,

        /**
         * Override quota limits.
         */
        OVERRIDE_QUOTA,

        /**
         * Apply a cost multiplier.
         */
        APPLY_MULTIPLIER,

        /**
         * Log the event (no side effect).
         */
        LOG
    }
}
