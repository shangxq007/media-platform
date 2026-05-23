package com.example.platform.billing.domain;

import java.time.Instant;
import java.util.Map;

public record SubscriptionContract(
        String contractId,
        String tenantId,
        String userId,
        String planKey,
        Instant periodStartAt,
        Instant periodEndAt,
        String lifecycleState,
        long basePriceMinor,
        String currencyCode,
        Map<String, Long> includedQuota,
        Map<String, Long> includedQuotaUsed,
        SubscriptionContractRole contractRole,
        String productCode) {

    public SubscriptionContract {
        if (contractRole == null) {
            contractRole = SubscriptionContractRole.BASE;
        }
    }

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(lifecycleState)
                && periodEndAt != null
                && periodEndAt.isAfter(now);
    }
}
