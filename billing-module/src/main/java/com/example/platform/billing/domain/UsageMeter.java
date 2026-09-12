package com.example.platform.billing.domain;

public record UsageMeter(
        String meterId,
        String meterKey,
        String name,
        String description,
        String unit,
        String aggregationType,
        String status) {
    /** Creates a catalog meter; this identity is unrelated to deterministic usage normalization. */
    public static UsageMeter create(String meterKey, String name, String description,
            String unit, String aggregationType) {
        return new UsageMeter("mtr_" + java.util.UUID.randomUUID().toString().replace("-", ""),
                meterKey, name, description, unit, aggregationType, "ACTIVE");
    }
}
