package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Cost reservation for a render job, created before execution.
 */
public record CostReservation(
        String reservationId,
        String tenantId,
        String userId,
        String renderJobId,
        double reservedAmount,
        String currency,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime finalizedAt) {

    public static CostReservation create(String tenantId, String userId, String renderJobId,
            double reservedAmount, String currency) {
        return new CostReservation(
                java.util.UUID.randomUUID().toString(),
                tenantId, userId, renderJobId, reservedAmount, currency,
                "RESERVED", OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(1), null);
    }

    public CostReservation released() {
        return new CostReservation(reservationId, tenantId, userId, renderJobId,
                reservedAmount, currency, "RELEASED", createdAt, expiresAt, OffsetDateTime.now());
    }

    public CostReservation adjusted(double actualAmount) {
        return new CostReservation(reservationId, tenantId, userId, renderJobId,
                actualAmount, currency, "ADJUSTED", createdAt, expiresAt, OffsetDateTime.now());
    }
}
