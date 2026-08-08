package com.example.platform.shared.authorization;

/**
 * Canonical authorization port — the single entry point through which the
 * security-sensitive path obtains an {@link AuthorizationDecision}.
 *
 * <p>The composition contract (APPD-CHV1) is: {@link AuthorizationDecision}
 * (security authorization) is evaluated FIRST and is INDEPENDENT of the separate
 * Entitlement → FeatureFlag → Capability → Quota composition. Entitlement, feature
 * flags, and quotas can never grant authorization (AR-AUTH-003/004/005/007/009).</p>
 *
 * <p>The port interface lives in shared-kernel so any module can depend on it. The
 * RBAC-backed implementation lives in identity-access-module (consuming the existing
 * {@code PermissionService}) and is injected at runtime.</p>
 */
public interface AuthorizationDecisionPort {

    /**
     * Decide whether {@code request} is allowed.
     *
     * @return the decision (never null)
     */
    AuthorizationDecision decide(AuthorizationRequest request);

    /**
     * Decide, throwing {@link AuthorizationDeniedException} (→ 403) when denied.
     * <p>Convenience for the common "fail-closed" boundary usage.</p>
     */
    default AuthorizationDecision requireAuthorized(AuthorizationRequest request) {
        AuthorizationDecision decision = decide(request);
        if (!decision.allowed()) {
            throw new AuthorizationDeniedException(decision);
        }
        return decision;
    }
}
