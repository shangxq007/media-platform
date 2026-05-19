package com.example.platform.extension.api.dto;

public record CreateRoutingRuleRequest(
        String ruleName,
        String sourceVersion,
        String targetVersion,
        String tenantId,
        String userId,
        String scene,
        int priority,
        int trafficPercent,
        String createdBy
) {}
