package com.example.platform.shared.events;

import java.time.Instant;

public record RenderDeliveryFailedEvent(
        String deliveryJobId,
        String renderJobId,
        String projectId,
        String tenantId,
        String destinationId,
        String protocol,
        String errorMessage,
        Instant failedAt) {}
