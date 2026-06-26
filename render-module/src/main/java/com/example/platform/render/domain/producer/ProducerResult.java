package com.example.platform.render.domain.producer;

import java.util.List;

/**
 * Result returned by a Producer after execution.
 * Contains produced product IDs, duration, warnings, errors.
 * No storage logic.
 */
public record ProducerResult(
        boolean success,
        List<String> producedProductIds,
        long executionDurationMs,
        List<String> warnings,
        String error) {

    public static ProducerResult success(List<String> productIds, long durationMs) {
        return new ProducerResult(true, productIds, durationMs, List.of(), null);
    }

    public static ProducerResult failure(String error, long durationMs) {
        return new ProducerResult(false, List.of(), durationMs, List.of(), error);
    }
}
