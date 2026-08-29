package com.example.platform.capability.effective;

/** Quota-specific evidence kept distinct from entitlement evidence. */
public record QuotaDecisionDetails(
        String quotaKey,
        long limitUnits,
        long usedUnits,
        long requestedUnits) {

    public QuotaDecisionDetails {
        quotaKey = EffectiveCapabilityValidation.requireNonBlank(quotaKey, "quotaKey");
    }
}
