package com.example.platform.commerce.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckoutOrchestratorBusinessLifecycleTest {

    private CommerceCatalogService catalogService;
    private CheckoutOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        catalogService = new CommerceCatalogService();
        CommerceCartService cartService = new CommerceCartService(catalogService);
        orchestrator = new CheckoutOrchestrator(catalogService, cartService, null, null, null, null, new SimpleMeterRegistry());
    }

    @Test
    void completeCheckoutWorkflow() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription",
                "https://example.com/success", "https://example.com/cancel");
        CheckoutSessionResponse response = orchestrator.createSession(request);

        assertNotNull(response.checkoutSessionId());
        assertEquals(1, orchestrator.getActiveSessionsCount());

        PurchaseOrderCreatedEvent event = orchestrator.confirmCheckout(response.checkoutSessionId());

        assertNotNull(event.orderId());
        assertEquals("CONFIRMED", event.orderStatus());
        assertEquals(99.99, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);
        assertTrue(orchestrator.getActiveSessionsCount() < 1);
    }

    @Test
    void cancelWorkflow() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "basic_monthly", null, "subscription",
                "https://example.com/success", "https://example.com/cancel");
        CheckoutSessionResponse response = orchestrator.createSession(request);

        PurchaseOrderCreatedEvent cancelledEvent = orchestrator.cancelCheckout(response.checkoutSessionId());

        assertNotNull(cancelledEvent.orderId());
        assertEquals("CANCELLED", cancelledEvent.orderStatus());
        assertEquals(0.0, cancelledEvent.orderValue(), 0.01);
        assertEquals(0.0, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);
    }

    @Test
    void multipleTenantIsolation() {
        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", null, "subscription", "https://example.com/success", null));
        CheckoutIntent intent1 = new CheckoutIntent("tenant-1", "pro_monthly", "subscription",
                "https://example.com/success", null);
        CheckoutSession session1 = orchestrator.createCheckoutSession(intent1);
        orchestrator.confirmCheckout(session1.checkoutSessionId());

        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-2", "basic_monthly", null, "subscription", "https://example.com/success", null));
        CheckoutIntent intent2 = new CheckoutIntent("tenant-2", "basic_monthly", "subscription",
                "https://example.com/success", null);
        CheckoutSession session2 = orchestrator.createCheckoutSession(intent2);
        orchestrator.confirmCheckout(session2.checkoutSessionId());

        assertEquals(99.99, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);
        assertEquals(29.99, orchestrator.getTotalRevenueForTenant("tenant-2"), 0.01);
        assertTrue(orchestrator.getRecentEvents("tenant-1", 10).size() >= 1);
        assertTrue(orchestrator.getRecentEvents("tenant-2", 10).size() >= 1);
    }

    @Test
    void businessRuleValidation() {
        // Enterprise is now available to any non-blank tenant — no longer throws for "unauthorized-tenant"
        // Subscription without success URL should still throw
        assertThrows(IllegalArgumentException.class, () -> orchestrator.createSession(
                new CreateCheckoutSessionRequest(
                        "tenant-1", "basic_monthly", null, "subscription", null, "https://example.com/cancel")));
    }

    @Test
    void creditPackDoesNotRequireSuccessUrl() {
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "credit_pack_50", null, null, null, null);
        CheckoutSessionResponse response = orchestrator.createSession(request);
        assertNotNull(response.checkoutSessionId());
    }
}
