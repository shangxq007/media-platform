package com.example.platform.commerce.domain;

public record CheckoutIntent(String tenantId, String canonicalProductCode, String purchaseMode, String successUrl, String cancelUrl) {}
