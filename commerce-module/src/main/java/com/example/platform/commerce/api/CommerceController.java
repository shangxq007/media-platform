package com.example.platform.commerce.api;

import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.domain.PurchaseOrderCreatedEvent;
import com.example.platform.commerce.app.CheckoutOrchestrator;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/commerce")
public class CommerceController {
    private final CheckoutOrchestrator checkoutOrchestrator;

    public CommerceController(CheckoutOrchestrator checkoutOrchestrator) {
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @PostMapping("/checkout-sessions")
    public CheckoutSessionResponse createCheckoutSession(@RequestBody CreateCheckoutSessionRequest request) {
        return checkoutOrchestrator.createSession(request);
    }

    @GetMapping("/events/recent/{tenantId}")
    public List<PurchaseOrderCreatedEvent> getRecentEvents(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "10") int limit) {
        return checkoutOrchestrator.getRecentEvents(tenantId, limit);
    }

    @GetMapping("/revenue/{tenantId}")
    public double getTotalRevenueForTenant(@PathVariable String tenantId) {
        return checkoutOrchestrator.getTotalRevenueForTenant(tenantId);
    }

    @GetMapping("/sessions/active-count/{tenantId}")
    public long getActiveSessionsCount(@PathVariable String tenantId) {
        return checkoutOrchestrator.getActiveSessionsCount();
    }

    @PostMapping("/checkout-sessions/{sessionId}/cancel")
    public PurchaseOrderCreatedEvent cancelCheckout(@PathVariable String sessionId) {
        return checkoutOrchestrator.cancelCheckout(sessionId);
    }

    @GetMapping("/orders/revenue/{tenantId}")
    public double getTotalRevenueForTenant(@PathVariable String tenantId) {
        return checkoutOrchestrator.getTotalRevenueForTenant(tenantId);
    }

    @GetMapping("/orders/recent/{tenantId}")
    public List<PurchaseOrderCreatedEvent> getRecentOrders(@PathVariable String tenantId, 
                                                           @RequestParam(defaultValue = "10") int limit) {
        return checkoutOrchestrator.getRecentEvents(tenantId, limit);
    }
}