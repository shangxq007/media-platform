package com.example.platform.render.domain.governance;

import java.time.Instant;
import java.util.Map;

/**
 * Meter event — records a consumption fact. Immutable. Never prices.
 */
public record MeterEvent(
        String eventId,
        String meterName,
        double quantity,
        String unit,
        Instant occurredAt,
        MeterAttribution attribution,
        Map<String, Object> attributes) {

    public static MeterEvent of(String meterName, double quantity, String unit,
                                  MeterAttribution attribution) {
        return new MeterEvent("met-" + System.currentTimeMillis(),
                meterName, quantity, unit, Instant.now(), attribution, Map.of());
    }
}
