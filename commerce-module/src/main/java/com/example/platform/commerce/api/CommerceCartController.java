package com.example.platform.commerce.api;

import com.example.platform.commerce.api.dto.*;
import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.commerce.app.CommerceCartService;
import com.example.platform.commerce.domain.CommerceCart;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/commerce/carts")
public class CommerceCartController {

    private final CommerceCartService cartService;
    private final CheckoutOrchestrator checkoutOrchestrator;

    public CommerceCartController(CommerceCartService cartService, CheckoutOrchestrator checkoutOrchestrator) {
        this.cartService = cartService;
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @PostMapping
    public CommerceCart createCart(@RequestBody CreateCartRequest request) {
        return cartService.createCart(request.tenantId(), request.userId());
    }

    @GetMapping("/{cartId}")
    public CommerceCart getCart(@PathVariable String cartId) {
        return cartService.getCart(cartId);
    }

    @PostMapping("/{cartId}/lines")
    public CommerceCart addLine(@PathVariable String cartId, @RequestBody AddCartLineRequest request) {
        return cartService.addLine(cartId, request.productCode(), request.quantity());
    }

    @DeleteMapping("/{cartId}/lines/{productCode}")
    public CommerceCart removeLine(@PathVariable String cartId, @PathVariable String productCode) {
        return cartService.removeLine(cartId, productCode);
    }

    @GetMapping("/{cartId}/total")
    public long cartTotal(@PathVariable String cartId) {
        return cartService.cartTotalMinor(cartId);
    }

    @PostMapping("/{cartId}/checkout-sessions")
    public CheckoutSessionResponse checkoutCart(
            @PathVariable String cartId,
            @RequestBody CartCheckoutRequest request) {
        CommerceCart cart = cartService.getCart(cartId);
        if (cart.lines().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        String primaryProduct = cart.lines().get(0).productCode();
        return checkoutOrchestrator.createSession(new CreateCheckoutSessionRequest(
                cart.tenantId(),
                primaryProduct,
                cart.userId(),
                null,
                request.successUrl(),
                request.cancelUrl()),
                cartId);
    }
}
