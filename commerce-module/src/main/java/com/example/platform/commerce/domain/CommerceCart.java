package com.example.platform.commerce.domain;

import java.time.Instant;
import java.util.List;

public record CommerceCart(
        String cartId,
        String tenantId,
        String userId,
        List<CartLineItem> lines,
        Instant createdAt,
        Instant updatedAt) {
}
