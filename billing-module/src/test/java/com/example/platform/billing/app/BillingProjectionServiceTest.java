package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class BillingProjectionServiceTest {

    private BillingProjectionService service;

    @BeforeEach
    void setUp() {
        service = new BillingProjectionService(null, null);
    }

    @Test
    void currentStateReturnsDefaultForUnknownSubject() {
        BillingState state = service.currentState("unknown-subject");

        assertNotNull(state);
        assertEquals("unknown-subject", state.subjectId());
        assertEquals("active", state.contractState());
        assertNotNull(state.periodEndAt());
        assertEquals("pro_monthly", state.canonicalProductCode());
    }

    @Test
    void activateSubscriptionCreatesBillingState() {
        Instant now = Instant.now();
        SubscriptionContract contract = new SubscriptionContract(
                "sub_123", "tenant-1", "tenant-1", "pro_monthly",
                now, now.plusSeconds(86400 * 30L),
                "active", 0L, "USD", Map.of(), Map.of()
        );

        BillingState state = service.activateSubscription(contract);

        assertNotNull(state);
        assertEquals("tenant-1", state.subjectId());
        assertEquals("active", state.contractState());
        assertEquals("pro_monthly", state.canonicalProductCode());
        assertEquals(contract.periodEndAt(), state.periodEndAt());
    }

    @Test
    void activateSubscriptionStoresContract() {
        Instant now = Instant.now();
        SubscriptionContract contract = new SubscriptionContract(
                "sub_123", "tenant-1", "tenant-1", "pro_monthly",
                now, now.plusSeconds(86400 * 30L),
                "active", 0L, "USD", Map.of(), Map.of()
        );

        service.activateSubscription(contract);

        SubscriptionContract stored = service.getContract("sub_123");
        assertNotNull(stored);
        assertEquals("tenant-1", stored.userId());
        assertEquals("pro_monthly", stored.planKey());
    }

    @Test
    void activateSubscriptionStoresBillingState() {
        Instant now = Instant.now();
        SubscriptionContract contract = new SubscriptionContract(
                "sub_123", "tenant-1", "tenant-1", "pro_monthly",
                now, now.plusSeconds(86400 * 30L),
                "active", 0L, "USD", Map.of(), Map.of()
        );

        service.activateSubscription(contract);

        BillingState state = service.getBillingState("tenant-1");
        assertNotNull(state);
        assertEquals("active", state.contractState());
    }

    @Test
    void activateSubscriptionWithNullThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                service.activateSubscription(null));
    }

    @Test
    void updateInvoiceCreatesEvent() {
        InvoiceProjectionUpdatedEvent event = service.updateInvoice("inv_123", "tenant-1", "issued");

        assertNotNull(event);
        assertEquals("inv_123", event.invoiceId());
        assertEquals("tenant-1", event.subjectId());
        assertEquals("issued", event.invoiceStatus());
    }

    @Test
    void updateInvoiceWithNullIdGeneratesId() {
        InvoiceProjectionUpdatedEvent event = service.updateInvoice(null, "tenant-1", "paid");

        assertNotNull(event);
        assertTrue(event.invoiceId().startsWith("inv_"));
        assertEquals("tenant-1", event.subjectId());
        assertEquals("paid", event.invoiceStatus());
    }

    @Test
    void getBillingStateReturnsNullForUnknownSubscription() {
        assertNull(service.getBillingState("unknown"));
    }

    @Test
    void getContractReturnsNullForUnknownContract() {
        assertNull(service.getContract("unknown"));
    }

    @Test
    void getInvoiceEventsReturnsAllEvents() {
        service.updateInvoice("inv_1", "tenant-1", "issued");
        service.updateInvoice("inv_2", "tenant-1", "paid");

        List<InvoiceProjectionUpdatedEvent> events = service.getInvoiceEvents();
        assertEquals(2, events.size());
    }

    @Test
    void createBillingEventReturnsEvent() {
        BillingEvent event = service.createBillingEvent("subscription.activated", "tenant-1", "pro_monthly", "active");

        assertNotNull(event);
        assertEquals("subscription.activated", event.eventType());
        assertEquals(1, event.eventVersion());
        assertEquals("tenant-1", event.subjectId());
        assertEquals("pro_monthly", event.canonicalProductCode());
        assertEquals("active", event.state());
    }

    @Test
    void createContractReturnsContractWithGeneratedId() {
        SubscriptionContract contract = service.createContract("tenant-1", "pro_monthly", "active", 30);

        assertNotNull(contract);
        assertTrue(contract.contractId().startsWith("sub_"));
        assertEquals("tenant-1", contract.userId());
        assertEquals("pro_monthly", contract.planKey());
        assertEquals("active", contract.lifecycleState());
        assertNotNull(contract.periodStartAt());
        assertNotNull(contract.periodEndAt());
    }

    @Test
    void createContractStoresContract() {
        SubscriptionContract contract = service.createContract("tenant-1", "pro_monthly", "active", 30);

        SubscriptionContract stored = service.getContract(contract.contractId());
        assertNotNull(stored);
        assertEquals(contract.contractId(), stored.contractId());
    }

    @Test
    void activateSubscriptionOverwritesExistingState() {
        Instant now = Instant.now();
        SubscriptionContract contract1 = new SubscriptionContract(
                "sub_1", "tenant-1", "tenant-1", "basic_monthly",
                now, now.plusSeconds(86400 * 30L),
                "active", 0L, "USD", Map.of(), Map.of()
        );
        SubscriptionContract contract2 = new SubscriptionContract(
                "sub_2", "tenant-1", "tenant-1", "pro_monthly",
                now, now.plusSeconds(86400 * 60L),
                "active", 0L, "USD", Map.of(), Map.of()
        );

        service.activateSubscription(contract1);
        service.activateSubscription(contract2);

        BillingState state = service.getBillingState("tenant-1");
        assertEquals("pro_monthly", state.canonicalProductCode());
    }
}
