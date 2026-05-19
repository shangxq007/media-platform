package com.example.platform.shared.cost;

import java.time.OffsetDateTime;

/**
 * Port interface for cost reservation, implemented by billing module.
 */
public interface CostReservationPort {
    CostReservation createReservation(String tenantId, String userId, String renderJobId,
            double estimatedCost, String currency);
    CostReservation finalizeReservation(String renderJobId, double actualCost);
    CostReservation releaseReservation(String renderJobId);

    record CostReservation(
            String reservationId,
            String tenantId,
            String userId,
            String renderJobId,
            double reservedAmount,
            String currency,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            OffsetDateTime finalizedAt) {}
}
