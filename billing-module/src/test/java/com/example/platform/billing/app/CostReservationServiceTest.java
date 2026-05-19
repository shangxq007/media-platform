package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostReservationServiceTest {

    private CostReservationService service;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new CostReservationService(eventPublisher);
    }

    @Test
    void shouldCreateReservation() {
        CostReservation reservation = service.createReservation("tenant-1", "user-1", "job-1", 5.0, "USD");
        assertNotNull(reservation);
        assertEquals("tenant-1", reservation.tenantId());
        assertEquals("user-1", reservation.userId());
        assertEquals("job-1", reservation.renderJobId());
        assertEquals(5.0, reservation.reservedAmount());
        assertEquals("USD", reservation.currency());
        assertEquals("RESERVED", reservation.status());
        verify(eventPublisher).publishEvent(any(com.example.platform.shared.events.CostReservationCreatedEvent.class));
    }

    @Test
    void shouldFinalizeReservation() {
        service.createReservation("tenant-1", "user-1", "job-1", 5.0, "USD");
        CostReservation finalized = service.finalizeReservation("job-1", 4.5);
        assertNotNull(finalized);
        assertEquals(4.5, finalized.reservedAmount());
        assertEquals("ADJUSTED", finalized.status());
        assertNotNull(finalized.finalizedAt());
    }

    @Test
    void shouldReleaseReservation() {
        service.createReservation("tenant-1", "user-1", "job-1", 5.0, "USD");
        CostReservation released = service.releaseReservation("job-1");
        assertNotNull(released);
        assertEquals("RELEASED", released.status());
        assertNotNull(released.finalizedAt());
    }

    @Test
    void shouldReturnNullForUnknownJob() {
        CostReservation result = service.finalizeReservation("unknown-job", 1.0);
        assertNull(result);
    }

    @Test
    void shouldFindReservationByJob() {
        service.createReservation("tenant-1", "user-1", "job-1", 5.0, "USD");
        CostReservation found = service.getReservationByJob("job-1");
        assertNotNull(found);
        assertEquals("job-1", found.renderJobId());
    }
}
