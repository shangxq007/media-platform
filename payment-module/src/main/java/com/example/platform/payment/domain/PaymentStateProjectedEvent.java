package com.example.platform.payment.domain;

public record PaymentStateProjectedEvent(String providerCode, String providerReference, String canonicalStatus) {}
