package com.example.platform.commerce.api;

import com.example.platform.commerce.api.dto.CanonicalProductResponse;
import com.example.platform.commerce.api.dto.ConfirmCheckoutRequest;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.api.dto.CheckoutSessionResponse;
import com.example.platform.commerce.domain.CanonicalProduct;
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

    @GetMapping("/products")
    public List<CanonicalProductResponse> listProducts() {
        return checkoutOrchestrator.listCatalogProducts().stream()
                .map(CommerceController::toProductResponse)
                .toList();
    }

    @PostMapping("/checkout-sessions")
    public CheckoutSessionResponse createCheckoutSession(@RequestBody CreateCheckoutSessionRequest request) {
        return checkoutOrchestrator.createSession(request);
    }

    @PostMapping("/checkout-sessions/{sessionId}/confirm")
    public PurchaseOrderCreatedEvent confirmCheckout(
            @PathVariable String sessionId,
            @RequestBody(required = false) ConfirmCheckoutRequest request) {
        String userId = request != null ? request.userId() : null;
        return checkoutOrchestrator.confirmCheckout(sessionId, userId);
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
        return checkoutOrchestrator.getActiveSessionsCount(tenantId);
    }

    @PostMapping("/checkout-sessions/{sessionId}/cancel")
    public PurchaseOrderCreatedEvent cancelCheckout(@PathVariable String sessionId) {
        return checkoutOrchestrator.cancelCheckout(sessionId);
    }

    private static CanonicalProductResponse toProductResponse(CanonicalProduct product) {
        return new CanonicalProductResponse(
                product.productCode(),
                product.purchaseModeName(),
                product.lineType().name(),
                product.featureBundleCode(),
                product.quotaProfileCode(),
                product.planKey(),
                product.tierKey(),
                product.bundleKey(),
                product.creditAmountMinor(),
                product.includedSeats(),
                product.seatFeatureKey(),
                product.priceMinor(),
                product.currencyCode(),
                product.displayName());
    }
}
