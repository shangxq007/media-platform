package com.example.platform.billing.domain;

public record BillingMeter(
        String meterKey,
        String name,
        String unit,
        String aggregationType,
        String status) {
}
