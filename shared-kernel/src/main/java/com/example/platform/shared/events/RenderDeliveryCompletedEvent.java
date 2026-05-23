package com.example.platform.shared.events;

import java.time.Instant;

public record RenderDeliveryCompletedEvent(
        String deliveryJobId,
        String renderJobId,
        String projectId,
        String tenantId,
        String destinationId,
        String protocol,
        String remoteUri,
        Instant completedAt) {}
