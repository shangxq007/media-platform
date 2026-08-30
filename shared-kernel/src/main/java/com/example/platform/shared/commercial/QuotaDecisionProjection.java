package com.example.platform.shared.commercial;

/** Read-only typed input to later application composition from Quota authority. */
@FunctionalInterface
public interface QuotaDecisionProjection {
    QuotaDecision decide(PrincipalRef principal, String quotaKey, long requestedUnits);
}
