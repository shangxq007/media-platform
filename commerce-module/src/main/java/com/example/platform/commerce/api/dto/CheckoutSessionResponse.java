package com.example.platform.commerce.api.dto;

public record CheckoutSessionResponse(String checkoutSessionId, String redirectUrl, String providerHint) {}
