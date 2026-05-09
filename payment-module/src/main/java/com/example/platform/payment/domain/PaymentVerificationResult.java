package com.example.platform.payment.domain;

public record PaymentVerificationResult(boolean verified, String externalState, String canonicalStatus) {}
