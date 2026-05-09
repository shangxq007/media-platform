package com.example.platform.payment.domain;

public record VerifyPaymentCommand(String providerReference, String rawPayload) {}
