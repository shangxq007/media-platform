package com.example.platform.shared.authorization;

/**
 * Creates a typed denial when a mutation has no complete authorization authority.
 *
 * <p>This is containment, not an authorization decision implementation. Callers use it only while
 * canonical actor, tenant, resource-owner, and action authorization cannot be established. Request
 * fields and fallback identities must never be substituted for that missing authority.
 */
public final class FailClosedAuthorization {

    private FailClosedAuthorization() {}

    public static AuthorizationDeniedException unavailable(String operation) {
        return new AuthorizationDeniedException(AuthorizationDecision.deny(
                "AUTHORIZATION_UNAVAILABLE",
                "FAIL_CLOSED_CONTAINMENT",
                operation + " is unavailable until canonical authorization is established"));
    }
}
