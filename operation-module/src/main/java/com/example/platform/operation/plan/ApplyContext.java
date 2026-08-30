package com.example.platform.operation.plan;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (§22): typed apply context binding the
 * durable ApplyCommandId, target ref, expected head, tenant, principal and the exact
 * AuthorizationDecision to one apply attempt. Apply verifies authorization
 * PlanDigest == plan PlanDigest and authorization context == apply context.
 */
public record ApplyContext(
        String applyCommandId,
        TargetRevisionRef targetRef,
        String expectedHeadRevisionId,
        String tenantId,
        String principalRef,
        AuthorizationDecision authorization) {

    public ApplyContext {
        if (applyCommandId == null || applyCommandId.isBlank()) {
            throw new IllegalArgumentException("applyCommandId required");
        }
        if (targetRef == null || authorization == null) {
            throw new IllegalArgumentException("targetRef and authorization required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
    }
}
