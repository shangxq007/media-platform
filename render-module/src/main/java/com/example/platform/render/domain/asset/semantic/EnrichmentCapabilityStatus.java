package com.example.platform.render.domain.asset.semantic;

import java.time.Instant;

/**
 * Tracks the status of a specific AI enrichment capability (PROBE, ASR, OCR, VISION, EMBEDDING).
 * Lives within AssetSemanticMetadata, not in a separate table.
 */
public record EnrichmentCapabilityStatus(
        String capability,
        String status,       // PENDING, IN_PROGRESS, COMPLETED, FAILED
        String provider,
        String model,
        Instant startedAt,
        Instant completedAt,
        int attemptCount,
        String lastError,
        long processingDurationMs) {

    public static EnrichmentCapabilityStatus pending(String capability) {
        return new EnrichmentCapabilityStatus(capability, "PENDING", null, null,
                null, null, 0, null, 0);
    }

    public static EnrichmentCapabilityStatus completed(String capability, String provider,
                                                         String model, long durationMs) {
        Instant now = Instant.now();
        return new EnrichmentCapabilityStatus(capability, "COMPLETED", provider, model,
                now, now, 1, null, durationMs);
    }

    public static EnrichmentCapabilityStatus failed(String capability, String provider,
                                                      String error, long durationMs) {
        return new EnrichmentCapabilityStatus(capability, "FAILED", provider, null,
                Instant.now(), null, 0, error, durationMs);
    }
}
