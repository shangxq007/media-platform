package com.example.platform.billing.app;

import com.example.platform.shared.cost.CostReservationPort;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Adapter that exposes CostReservationService as CostReservationPort.
 */
@Component
public class CostReservationPortAdapter implements CostReservationPort {

    private final CostReservationService costReservationService;

    public CostReservationPortAdapter(CostReservationService costReservationService) {
        this.costReservationService = costReservationService;
    }

    @Override
    public CostReservationPort.CostReservation createReservation(String tenantId, String userId,
            String renderJobId, double estimatedCost, String currency) {
        com.example.platform.billing.domain.CostReservation res = costReservationService.createReservation(
                tenantId, userId, renderJobId, estimatedCost, currency);
        return new CostReservationPort.CostReservation(res.reservationId(), res.tenantId(), res.userId(),
                res.renderJobId(), res.reservedAmount(), res.currency(), res.status(),
                res.createdAt(), res.expiresAt(), res.finalizedAt());
    }

    @Override
    public CostReservationPort.CostReservation finalizeReservation(String renderJobId, double actualCost) {
        com.example.platform.billing.domain.CostReservation res = costReservationService.finalizeReservation(renderJobId, actualCost);
        if (res == null) return null;
        return new CostReservationPort.CostReservation(res.reservationId(), res.tenantId(), res.userId(),
                res.renderJobId(), res.reservedAmount(), res.currency(), res.status(),
                res.createdAt(), res.expiresAt(), res.finalizedAt());
    }

    @Override
    public CostReservationPort.CostReservation releaseReservation(String renderJobId) {
        com.example.platform.billing.domain.CostReservation res = costReservationService.releaseReservation(renderJobId);
        if (res == null) return null;
        return new CostReservationPort.CostReservation(res.reservationId(), res.tenantId(), res.userId(),
                res.renderJobId(), res.reservedAmount(), res.currency(), res.status(),
                res.createdAt(), res.expiresAt(), res.finalizedAt());
    }
}
