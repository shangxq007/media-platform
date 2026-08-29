package com.example.platform.commerce.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckoutOrchestratorTest {

    private CommerceCatalogService catalogService;
    private CheckoutOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        catalogService = CatalogTestFixtures.catalog();
        CommerceCartService cartService = new CommerceCartService(catalogService);
        orchestrator = new CheckoutOrchestrator(catalogService, cartService, null, null, null, null, new SimpleMeterRegistry());
    }

    @Test
    void createSessionWithValidRequestReturnsSuccess() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription",
                "https://example.com/success", "https://example.com/cancel"
        );

        CheckoutSessionResponse response = orchestrator.createSession(request);

        assertNotNull(response);
        assertTrue(response.checkoutSessionId().startsWith("chk_"));
        assertEquals("https://example.com/success", response.redirectUrl());
        assertEquals("internal", response.providerHint());
    }

    @Test
    void confirmCheckoutCreatesOrderWithCorrectValue() {
        CreateCheckoutSessionRequest sessionRequest = new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription",
                "https://example.com/success", "https://example.com/cancel"
        );
        CheckoutSessionResponse sessionResponse = orchestrator.createSession(sessionRequest);

        PurchaseOrderCreatedEvent event = orchestrator.confirmCheckout(sessionResponse.checkoutSessionId());

        assertNotNull(event);
        assertEquals("tenant-1", event.tenantId());
        assertEquals("pro_monthly", event.canonicalProductCode());
        assertEquals(9999, event.amountMinorSnapshot());
    }

    @Test
    void checkoutKeepsImmutableOfferingPriceSnapshotAfterCatalogChange() {
        var response = orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription", "https://example.com/success", null));
        var changed = CatalogTestFixtures.offering("basic_monthly");
        doReturn(changed).when(catalogService).requireHistorical(any(), anyString(), anyLong());

        var event = orchestrator.confirmCheckout(response.checkoutSessionId());

        assertEquals("offer-pro_monthly", event.offeringId());
        assertEquals(1, event.offeringVersion());
        assertEquals("price-pro_monthly", event.commercialPriceReference().key());
        assertEquals(9999, event.amountMinorSnapshot());
        assertEquals("USD", event.currencyCodeSnapshot());
    }

    @Test
    void calculateRevenueForTenant() {
        // Create and confirm sessions for tenant-1
        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription", "https://example.com/success", null));
        CheckoutIntent intent1 = new CheckoutIntent("tenant-1", "pro_monthly", "subscription", "https://example.com/success", null);
        CheckoutSession session1 = orchestrator.createCheckoutSession(intent1);
        orchestrator.confirmCheckout(session1.checkoutSessionId());

        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-1", "basic_monthly", null, "subscription", "https://example.com/success", null));
        CheckoutIntent intent2 = new CheckoutIntent("tenant-1", "basic_monthly", "subscription", "https://example.com/success", null);
        CheckoutSession session2 = orchestrator.createCheckoutSession(intent2);
        orchestrator.confirmCheckout(session2.checkoutSessionId());

        long revenue = orchestrator.getTotalRevenueForTenant("tenant-1");
        assertEquals(12998, revenue);
    }
}
