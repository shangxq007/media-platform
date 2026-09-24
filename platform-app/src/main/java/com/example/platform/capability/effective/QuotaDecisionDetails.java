package com.example.platform.capability.effective;

import java.math.BigDecimal;

/** Quota-specific evidence kept distinct from entitlement evidence. */
public record QuotaDecisionDetails(
        String quotaKey,
        BigDecimal limitUnits,
        BigDecimal usedUnits,
        BigDecimal requestedUnits) {

    public QuotaDecisionDetails {
        quotaKey = EffectiveCapabilityValidation.requireNonBlank(quotaKey, "quotaKey");
    }

    public QuotaDecisionDetails(String quotaKey, long limitUnits, long usedUnits, long requestedUnits) {
        this(quotaKey, BigDecimal.valueOf(limitUnits), BigDecimal.valueOf(usedUnits), BigDecimal.valueOf(requestedUnits));
    }
}
