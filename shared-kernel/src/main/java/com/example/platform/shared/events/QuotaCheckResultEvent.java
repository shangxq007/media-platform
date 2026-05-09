package com.example.platform.shared.events;

public record QuotaCheckResultEvent(
        String tenantId,
        String featureCode,
        int requestedAmount,
        boolean allowed,
        int remaining) {}
