package com.example.platform.delivery.api.dto;

public record DeliveryPolicyResponse(
        String id,
        String tenantId,
        String projectId,
        String destinationId,
        String artifactSelector,
        String pathTemplate,
        String triggerMode,
        boolean enabled) {}
