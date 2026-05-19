package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a cost reservation is created.
 */
public record CostReservationCreatedEvent(
        String reservationId,
        String tenantId,
        String userId,
        String renderJobId,
        double reservedAmount,
        String currency,
        Instant createdAt) {}
