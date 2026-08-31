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
        com.example.platform.shared.authorization.CanonicalActor actor,
        AuthorizationDecision authorization) {

    public ApplyContext {
        if (applyCommandId == null || applyCommandId.isBlank()) {
            throw new IllegalArgumentException("applyCommandId required");
        }
        if (targetRef == null || actor == null || authorization == null) {
            throw new IllegalArgumentException("targetRef, actor and authorization required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
        if (!tenantId.equals(actor.tenantId())) {
            throw new IllegalArgumentException("actor tenant must equal apply tenant");
        }
    }

    public String principalRef() {
        return actor.actorId();
    }
}
