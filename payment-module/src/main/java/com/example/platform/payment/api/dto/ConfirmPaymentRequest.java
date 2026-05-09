package com.example.platform.payment.api.dto;

public record ConfirmPaymentRequest(String providerCode, String providerReference, String payload) {}
