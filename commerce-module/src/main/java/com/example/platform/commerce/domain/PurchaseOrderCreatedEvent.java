package com.example.platform.commerce.domain;

import java.time.Instant;

public record PurchaseOrderCreatedEvent(String orderId, String tenantId, String canonicalProductCode, String orderStatus) {

    public PurchaseOrderCreatedEvent(String orderId, String tenantId, String canonicalProductCode) {
        this(orderId, tenantId, canonicalProductCode, "CREATED");
    }

    public double orderValue() {
        if ("CANCELLED".equals(orderStatus)) {
            return 0.0;
        }
        return switch (canonicalProductCode) {
            case "pro_monthly" -> 99.99;
            case "basic_monthly" -> 29.99;
            case "enterprise_monthly" -> 299.99;
            default -> 0.0;
        };
    }

    public Instant eventTime() {
        return Instant.now();
    }
}
