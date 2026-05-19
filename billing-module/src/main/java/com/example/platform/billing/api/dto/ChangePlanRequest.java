package com.example.platform.billing.api.dto;

public record ChangePlanRequest(
        String contractId,
        String newPlanKey,
        int periodDays) {
}
