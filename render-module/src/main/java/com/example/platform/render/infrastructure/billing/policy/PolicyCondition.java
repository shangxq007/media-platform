package com.example.platform.render.infrastructure.billing.policy;

import java.util.Map;

/**
 * Condition that must be met for a policy to apply.
 * Conditions are evaluated against the billing context.
 */
public record PolicyCondition(
        ConditionType type,
        String field,
        Operator operator,
        Object value,
        Map<String, Object> metadata
) {
    /**
     * Create a simple equality condition.
     */
    public static PolicyCondition equals(ConditionType type, String field, Object value) {
        return new PolicyCondition(type, field, Operator.EQUALS, value, Map.of());
    }

    /**
     * Create a greater-than condition.
     */
    public static PolicyCondition greaterThan(ConditionType type, String field, Object value) {
        return new PolicyCondition(type, field, Operator.GREATER_THAN, value, Map.of());
    }

    /**
     * Create a less-than condition.
     */
    public static PolicyCondition lessThan(ConditionType type, String field, Object value) {
        return new PolicyCondition(type, field, Operator.LESS_THAN, value, Map.of());
    }

    /**
     * Create an in-list condition.
     */
    public static PolicyCondition in(ConditionType type, String field, Object value) {
        return new PolicyCondition(type, field, Operator.IN, value, Map.of());
    }

    /**
     * Evaluate this condition against a context value.
     */
    public boolean evaluate(Object contextValue) {
        if (contextValue == null) return false;

        return switch (operator) {
            case EQUALS -> contextValue.equals(value);
            case NOT_EQUALS -> !contextValue.equals(value);
            case GREATER_THAN -> compareValues(contextValue, value) > 0;
            case LESS_THAN -> compareValues(contextValue, value) < 0;
            case GREATER_THAN_OR_EQUALS -> compareValues(contextValue, value) >= 0;
            case LESS_THAN_OR_EQUALS -> compareValues(contextValue, value) <= 0;
            case IN -> {
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        if (contextValue.equals(item)) yield true;
                    }
                }
                yield false;
            }
            case NOT_IN -> {
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        if (contextValue.equals(item)) yield false;
                    }
                }
                yield true;
            }
            case CONTAINS -> contextValue.toString().contains(value.toString());
            case STARTS_WITH -> contextValue.toString().startsWith(value.toString());
        };
    }

    private int compareValues(Object a, Object b) {
        if (a instanceof Number n1 && b instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue());
        }
        if (a instanceof Comparable<?> c1) {
            @SuppressWarnings("unchecked")
            int result = ((Comparable<Object>) c1).compareTo(b);
            return result;
        }
        return 0;
    }

    /**
     * Condition types.
     */
    public enum ConditionType {
        TENANT_ID,
        WORKSPACE_ID,
        USER_ID,
        ACTION_TYPE,
        PROVIDER,
        PRESET,
        TIER,
        USAGE_AMOUNT,
        COST_AMOUNT,
        DURATION_SECONDS,
        OUTPUT_SIZE,
        GPU_ENABLED,
        FEATURE_KEY,
        CUSTOM
    }

    /**
     * Comparison operators.
     */
    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        LESS_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN_OR_EQUALS,
        IN,
        NOT_IN,
        CONTAINS,
        STARTS_WITH
    }
}
