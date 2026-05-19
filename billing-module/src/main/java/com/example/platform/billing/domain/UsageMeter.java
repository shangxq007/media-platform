package com.example.platform.billing.domain;

public record UsageMeter(
        String meterId,
        String meterKey,
        String name,
        String description,
        String unit,
        String aggregationType,
        String status) {
}
