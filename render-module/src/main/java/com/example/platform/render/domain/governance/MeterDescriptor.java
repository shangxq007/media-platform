package com.example.platform.render.domain.governance;

import java.util.List;

/**
 * Meter descriptor — a component declares its measurable units.
 * Never prices. Never bills. Metadata only.
 */
public record MeterDescriptor(
        String capability,
        MeterCategory category,
        List<String> meters,
        List<String> units) {

    public enum MeterCategory {
        INSTANT_USAGE,
        RESOURCE_OCCUPANCY,
        TRANSFER,
        RESERVED_CAPACITY
    }

    public static MeterDescriptor asr() {
        return new MeterDescriptor("ASR", MeterCategory.INSTANT_USAGE,
                List.of("AUDIO_MINUTES", "PROCESSING_DURATION", "REQUEST_COUNT"),
                List.of("minutes", "seconds", "requests"));
    }

    public static MeterDescriptor storage() {
        return new MeterDescriptor("STORAGE", MeterCategory.RESOURCE_OCCUPANCY,
                List.of("STORAGE_OCCUPANCY"),
                List.of("GB-hours"));
    }
}
