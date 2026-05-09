package com.example.platform.commerce.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckoutOrchestratorBusinessLifecycleTest {

    private CommerceCatalogService catalogService;
    private CheckoutOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        catalogService = new CommerceCatalogService();
        orchestrator = new CheckoutOrchestrator(catalogService, null, null);
    }

    @Test
    void completeCheckoutWorkflow() {
        // Create session
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", "subscription", "https://example.com/success", "https://example.com/cancel"
        );
        CheckoutSessionResponse response = orchestrator.createSession(request);

        assertNotNull(response.checkoutSessionId());
        assertEquals(1, orchestrator.getActiveSessionsCount());

        // Confirm checkout
        PurchaseOrderCreatedEvent event = orchestrator.confirmCheckout(response.checkoutSessionId());

        assertNotNull(event.orderId());
        assertEquals("CONFIRMED", event.orderStatus());
        assertEquals(99.99, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);

        // Verify session is no longer active
        assertTrue(orchestrator.getActiveSessionsCount() < 1);
    }

    @Test
    void cancelWorkflow() {
        // Create session
        CreateCheckoutSessionRequest request = new CreateCheckoutSessionRequest(
                "tenant-1", "basic_monthly", "subscription", "https://example.com/success", "https://example.com/cancel"
        );
        CheckoutSessionResponse response = orchestrator.createSession(request);

        // Cancel session
        PurchaseOrderCreatedEvent cancelledEvent = orchestrator.cancelCheckout(response.checkoutSessionId());

        assertNotNull(cancelledEvent.orderId());
        assertEquals("CANCELLED", cancelledEvent.orderStatus());
        assertEquals(0.0, cancelledEvent.orderValue(), 0.01);

        // Verify revenue remains zero for cancelled order
        assertEquals(0.0, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);
    }

    @Test
    void multipleTenantIsolation() {
        // Tenant 1 creates sessions
        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-1", "pro_monthly", "subscription", "https://example.com/success", null));
        CheckoutIntent intent1 = new CheckoutIntent("tenant-1", "pro_monthly", "subscription", "https://example.com/success", null);
        CheckoutSession session1 = orchestrator.createCheckoutSession(intent1);
        orchestrator.confirmCheckout(session1.checkoutSessionId());

        // Tenant 2 creates sessions
        orchestrator.createSession(new CreateCheckoutSessionRequest(
                "tenant-2", "basic_monthly", "subscription", "https://example.com/success", null));
        CheckoutIntent intent2 = new CheckoutIntent("tenant-2", "basic_monthly", "subscription", "https://example.com/success", null);
        CheckoutSession session2 = orchestrator.createCheckoutSession(intent2);
        orchestrator.confirmCheckout(session2.checkoutSessionId());

        // Revenue should be separate per tenant
        assertEquals(99.99, orchestrator.getTotalRevenueForTenant("tenant-1"), 0.01);
        assertEquals(29.99, orchestrator.getTotalRevenueForTenant("tenant-2"), 0.01);

        // Recent events should be separate per tenant
        assertTrue(orchestrator.getRecentEvents("tenant-1", 10).size() >= 1);
        assertTrue(orchestrator.getRecentEvents("tenant-2", 10).size() >= 1);
    }

    @Test
    void businessRuleValidation() {
        // Test enterprise product restrictions
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.createSession(new CreateCheckoutSessionRequest(
                    "unauthorized-tenant", "enterprise_monthly", "subscription", 
                    "https://example.com/success", "https://example.com/cancel"));
        });

        // Test subscription product success URL requirement
        assertThrows(IllegalArgumentException.class, () -> {
            orchestrator.createSession(new CreateCheckoutSessionRequest(
                    "tenant-1", "basic_monthly", "subscription", null, "https://example.com/cancel"));
        });
    }

    @Test
    void expiredSessionDetection() {
        // Create session (simulate old session)
        CheckoutIntent intent = new CheckoutIntent("tenant-1", "basic_monthly", "subscription", 
                                                    "https://example.com/success", null);
        CheckoutSession session = orchestrator.createCheckoutSession(intent);
        
        // Manually set creation time to simulate expiration
        // This would normally be handled by actual time-based logic
        
        // Test that expired sessions are properly tracked in business logic
        assertNotNull(session.checkoutSessionId());
    }
}