package com.example.platform.operation.plan;

import java.time.OffsetDateTime;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT16/§20/OPI2): immutable authorization
 * decision binding exact PlanDigest + principal + exact apply authorization
 * context (project/workspace/tenant + target ref). Reuse on a different
 * target/principal/plan is rejected. No generalized IAM.
 */
public record AuthorizationDecision(
        boolean allowed,
        String planDigest,
        String principalRef,
        String projectId,
        String tenantId,
        String targetRefId,
        String policyVersion,
        OffsetDateTime issuedAt) {

    public AuthorizationDecision {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId required");
        }
    }

    public static AuthorizationDecision allow(String planDigest, String principalRef,
                                              String projectId, String tenantId,
                                              String targetRefId, String policyVersion) {
        return new AuthorizationDecision(true, planDigest, principalRef, projectId, tenantId,
                targetRefId, policyVersion, OffsetDateTime.now());
    }

    public static AuthorizationDecision deny(String planDigest, String principalRef,
                                             String projectId, String tenantId,
                                             String targetRefId, String policyVersion) {
        return new AuthorizationDecision(false, planDigest, principalRef, projectId, tenantId,
                targetRefId, policyVersion, OffsetDateTime.now());
    }
}
