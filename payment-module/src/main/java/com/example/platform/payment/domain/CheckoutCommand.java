package com.example.platform.payment.domain;

public record CheckoutCommand(String checkoutSessionId, String canonicalProductCode, String successUrl, String cancelUrl) {}
