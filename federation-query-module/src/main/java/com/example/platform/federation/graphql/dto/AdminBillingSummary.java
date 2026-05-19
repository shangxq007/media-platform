package com.example.platform.federation.graphql.dto;

public record AdminBillingSummary(
        MoneyDto usageAmount,
        MoneyDto estimatedRevenue,
        MoneyDto creditBalanceTotal
) {}
