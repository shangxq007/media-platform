package com.example.platform.operation.plan;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT19/PT20/§6): typed transaction context.
 * TargetRevisionRef identifies the mutable project head/ref being advanced;
 * ExpectedTargetHeadRevisionId is the exact head the apply expects. These are
 * transaction/application context, NEVER Operation semantic targets, NEVER
 * part of Timeline canonical content, NEVER part of PlanDigest.
 */
public record TargetRevisionRef(String refId) {

    public TargetRevisionRef {
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("refId required");
        }
    }

    public static final String MAIN_REF = "main";
}
