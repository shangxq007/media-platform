package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a cost reservation is released or adjusted.
 */
public record CostReservationReleasedEvent(
        String reservationId,
        String tenantId,
        String userId,
        String renderJobId,
        double reservedAmount,
        double actualAmount,
        String currency,
        Instant releasedAt) {}
