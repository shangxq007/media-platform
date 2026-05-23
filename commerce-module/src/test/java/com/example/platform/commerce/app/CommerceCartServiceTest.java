package com.example.platform.commerce.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommerceCartServiceTest {

    private CommerceCartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CommerceCartService(new CommerceCatalogService());
    }

    @Test
    void addLinesAndComputeTotal() {
        var cart = cartService.createCart("tenant-1", "user-1");
        cartService.addLine(cart.cartId(), "basic_monthly", 1);
        cartService.addLine(cart.cartId(), "credit_pack_50", 1);
        assertEquals(2, cartService.getCart(cart.cartId()).lines().size());
        assertTrue(cartService.cartTotalMinor(cart.cartId()) > 0);
        assertEquals(2, cartService.resolveLines(cart.cartId()).size());
    }
}
