package com.example.platform.identity.api.authorization;

import com.example.platform.shared.authorization.AuthorizationDecision;
import java.util.Objects;

/** Typed Identity denial. Transport adapters, not this contract, choose HTTP status. */
public final class AuthorizationDeniedException extends RuntimeException {
    private final AuthorizationDecision decision;
    public AuthorizationDeniedException(AuthorizationDecision decision) {
        super(Objects.requireNonNull(decision, "decision").detail());
        if (decision.allowed()) throw new IllegalArgumentException("An allowed decision is not a denial");
        this.decision = decision;
    }
    public AuthorizationDecision decision() { return decision; }
    public boolean isTenantBoundary() { return "TENANT_BOUNDARY".equals(decision.reasonCode()); }
}
