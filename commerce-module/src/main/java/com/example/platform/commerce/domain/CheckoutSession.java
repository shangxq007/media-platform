package com.example.platform.commerce.domain;

public record CheckoutSession(String checkoutSessionId, String tenantId, String canonicalProductCode, String redirectUrl, String providerHint) {}
