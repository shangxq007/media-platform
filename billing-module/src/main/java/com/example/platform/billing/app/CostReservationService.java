package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.events.CostReservationCreatedEvent;
import com.example.platform.shared.events.CostReservationReleasedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cost reservations for render jobs.
 */
@Service
public class CostReservationService {

    private static final Logger log = LoggerFactory.getLogger(CostReservationService.class);

    private final ConcurrentHashMap<String, CostReservation> reservations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> jobReservationIndex = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    public CostReservationService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Create a cost reservation for a render job.
     */
    public CostReservation createReservation(String tenantId, String userId, String renderJobId,
            double estimatedCost, String currency) {
        CostReservation reservation = CostReservation.create(tenantId, userId, renderJobId,
                estimatedCost, currency);
        reservations.put(reservation.reservationId(), reservation);
        jobReservationIndex.put(renderJobId, reservation.reservationId());
        eventPublisher.publishEvent(new CostReservationCreatedEvent(
                reservation.reservationId(), tenantId, userId, renderJobId,
                estimatedCost, currency, Instant.now()));
        log.info("CostReservationService: created reservation {} for job={}, amount={} {}",
                reservation.reservationId(), renderJobId, estimatedCost, currency);
        return reservation;
    }

    /**
     * Finalize a reservation with the actual cost.
     */
    public CostReservation finalizeReservation(String renderJobId, double actualCost) {
        String reservationId = jobReservationIndex.get(renderJobId);
        if (reservationId == null) {
            log.warn("CostReservationService: no reservation found for job={}", renderJobId);
            return null;
        }
        CostReservation existing = reservations.get(reservationId);
        if (existing == null) return null;

        CostReservation adjusted = existing.adjusted(actualCost);
        reservations.put(reservationId, adjusted);
        eventPublisher.publishEvent(new CostReservationReleasedEvent(
                reservationId, existing.tenantId(), existing.userId(), renderJobId,
                existing.reservedAmount(), actualCost, existing.currency(), Instant.now()));
        log.info("CostReservationService: finalized reservation {} for job={}, reserved={}, actual={}",
                reservationId, renderJobId, existing.reservedAmount(), actualCost);
        return adjusted;
    }

    /**
     * Release a reservation (job cancelled or failed before cost incurred).
     */
    public CostReservation releaseReservation(String renderJobId) {
        String reservationId = jobReservationIndex.get(renderJobId);
        if (reservationId == null) return null;
        CostReservation existing = reservations.get(reservationId);
        if (existing == null) return null;

        CostReservation released = existing.released();
        reservations.put(reservationId, released);
        jobReservationIndex.remove(renderJobId);
        eventPublisher.publishEvent(new CostReservationReleasedEvent(
                reservationId, existing.tenantId(), existing.userId(), renderJobId,
                existing.reservedAmount(), 0.0, existing.currency(), Instant.now()));
        log.info("CostReservationService: released reservation {} for job={}", reservationId, renderJobId);
        return released;
    }

    public CostReservation getReservation(String reservationId) {
        return reservations.get(reservationId);
    }

    public CostReservation getReservationByJob(String renderJobId) {
        String reservationId = jobReservationIndex.get(renderJobId);
        return reservationId != null ? reservations.get(reservationId) : null;
    }
}
