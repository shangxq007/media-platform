package com.example.platform.commerce.api.dto;

public record CartCheckoutRequest(
        String successUrl,
        String cancelUrl) {
}
