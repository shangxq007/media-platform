package com.example.platform.shared.events;

public record QuotaCheckRequestedEvent(
        String tenantId,
        String featureCode,
        int requestedAmount) {}
