package com.example.platform.shared.commercial;

/** Read-only typed input to later application composition from Entitlement authority. */
@FunctionalInterface
public interface EntitlementDecisionProjection {
    EntitlementDecision decide(PrincipalRef principal, String entitlementKey);
}
