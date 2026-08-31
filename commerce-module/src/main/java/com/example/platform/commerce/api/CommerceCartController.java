package com.example.platform.commerce.api;

import com.example.platform.commerce.api.dto.*;
import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.commerce.app.CommerceCartService;
import com.example.platform.commerce.domain.CommerceCart;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commerce/carts")
public class CommerceCartController {

    private final CommerceCartService cartService;
    private final CheckoutOrchestrator checkoutOrchestrator;

    public CommerceCartController(CommerceCartService cartService, CheckoutOrchestrator checkoutOrchestrator) {
        this.cartService = cartService;
        this.checkoutOrchestrator = checkoutOrchestrator;
    }

    @PostMapping
    public CommerceCart createCart(@RequestBody CreateCartRequest request) {
        throw FailClosedAuthorization.unavailable("commerce cart creation");
    }

    @GetMapping("/{cartId}")
    public CommerceCart getCart(@PathVariable String cartId) {
        return cartService.getCart(cartId);
    }

    @PostMapping("/{cartId}/lines")
    public CommerceCart addLine(@PathVariable String cartId, @RequestBody AddCartLineRequest request) {
        throw FailClosedAuthorization.unavailable("commerce cart line addition");
    }

    @DeleteMapping("/{cartId}/lines/{productCode}")
    public CommerceCart removeLine(@PathVariable String cartId, @PathVariable String productCode) {
        throw FailClosedAuthorization.unavailable("commerce cart line removal");
    }

    @GetMapping("/{cartId}/total")
    public long cartTotal(@PathVariable String cartId) {
        return cartService.cartTotalMinor(cartId);
    }

    @PostMapping("/{cartId}/checkout-sessions")
    public CheckoutSessionResponse checkoutCart(
            @PathVariable String cartId,
            @RequestBody CartCheckoutRequest request) {
        throw FailClosedAuthorization.unavailable("commerce cart checkout");
    }
}
