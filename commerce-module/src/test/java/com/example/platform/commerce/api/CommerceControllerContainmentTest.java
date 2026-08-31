package com.example.platform.commerce.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.commerce.api.dto.AddCartLineRequest;
import com.example.platform.commerce.api.dto.CartCheckoutRequest;
import com.example.platform.commerce.api.dto.ConfirmCheckoutRequest;
import com.example.platform.commerce.api.dto.CreateCartRequest;
import com.example.platform.commerce.api.dto.CreateCheckoutSessionRequest;
import com.example.platform.commerce.app.CheckoutOrchestrator;
import com.example.platform.commerce.app.CommerceCartService;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import org.junit.jupiter.api.Test;

class CommerceControllerContainmentTest {

    @Test
    void requestTenantUserAndCartIdentifiersCannotAuthorizeCommerceMutations() {
        CheckoutOrchestrator checkout = mock(CheckoutOrchestrator.class);
        CommerceCartService carts = mock(CommerceCartService.class);
        CommerceController commerce = new CommerceController(checkout);
        CommerceCartController cart = new CommerceCartController(carts, checkout);

        assertUnavailable(() -> commerce.createCheckoutSession(new CreateCheckoutSessionRequest(
                "request-tenant", "product", "request-user", "ONE_TIME", null, null)));
        assertUnavailable(() -> commerce.confirmCheckout(
                "request-session", new ConfirmCheckoutRequest("request-user")));
        assertUnavailable(() -> commerce.cancelCheckout("request-session"));
        assertUnavailable(() -> cart.createCart(new CreateCartRequest("request-tenant", "request-user")));
        assertUnavailable(() -> cart.addLine(
                "request-cart", new AddCartLineRequest("product", 1)));
        assertUnavailable(() -> cart.removeLine("request-cart", "product"));
        assertUnavailable(() -> cart.checkoutCart(
                "request-cart", new CartCheckoutRequest("success", "cancel")));

        verifyNoInteractions(checkout, carts);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
