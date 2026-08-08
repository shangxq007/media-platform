package com.example.platform.shared.authorization;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of an authorization decision.
 *
 * <p>Binary {@link #allowed} with a machine-readable {@link #reasonCode} and, where
 * available, a {@link #ruleRef} / {@code policyVersion} identifying the rule that
 * produced the outcome. {@link #detail} carries a human-readable explanation for
 * audit/response purposes only.</p>
 *
 * @param allowed    true = ALLOW, false = DENY
 * @param reasonCode stable code identifying why (e.g. "TENANT_BOUNDARY", "RBAC_DENY", "ALLOW")
 * @param ruleRef    reference to the rule/policy that decided (where available)
 * @param detail     human-readable explanation (optional)
 */
public record AuthorizationDecision(
        boolean allowed,
        String reasonCode,
        String ruleRef,
        String detail) {

    public AuthorizationDecision {
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    }

    public AuthorizationDecision(boolean allowed, String reasonCode) {
        this(allowed, reasonCode, null, null);
    }

    public static AuthorizationDecision allow(String ruleRef) {
        return new AuthorizationDecision(true, "ALLOW", ruleRef, null);
    }

    public static AuthorizationDecision allow(String ruleRef, String detail) {
        return new AuthorizationDecision(true, "ALLOW", ruleRef, detail);
    }

    public static AuthorizationDecision deny(String reasonCode, String ruleRef) {
        return new AuthorizationDecision(false, reasonCode, ruleRef, null);
    }

    public static AuthorizationDecision deny(String reasonCode, String ruleRef, String detail) {
        return new AuthorizationDecision(false, reasonCode, ruleRef, detail);
    }
}
