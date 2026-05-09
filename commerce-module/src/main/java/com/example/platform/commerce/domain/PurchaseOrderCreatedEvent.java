package com.example.platform.commerce.domain;

public record PurchaseOrderCreatedEvent(String orderId, String tenantId, String canonicalProductCode) {}
