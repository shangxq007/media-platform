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
        String productCode,
        long version) {

    public SubscriptionContract {
        if (contractRole == null) {
            contractRole = SubscriptionContractRole.BASE;
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    public SubscriptionContract(
            String contractId, String tenantId, String userId, String planKey,
            Instant periodStartAt, Instant periodEndAt, String lifecycleState,
            long basePriceMinor, String currencyCode, Map<String, Long> includedQuota,
            Map<String, Long> includedQuotaUsed, SubscriptionContractRole contractRole,
            String productCode) {
        this(contractId, tenantId, userId, planKey, periodStartAt, periodEndAt,
                lifecycleState, basePriceMinor, currencyCode, includedQuota,
                includedQuotaUsed, contractRole, productCode, 0L);
    }

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(lifecycleState)
                && periodEndAt != null
                && periodEndAt.isAfter(now);
    }
}
