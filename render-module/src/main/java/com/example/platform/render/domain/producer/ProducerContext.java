package com.example.platform.render.domain.producer;

import java.util.List;
import java.util.Map;

/**
 * Context passed to a Producer during execution.
 * Holds input products, output expectations, and execution hints.
 * No business logic.
 */
public record ProducerContext(
        String executionId,
        String tenantId,
        String projectId,
        List<String> inputProductIds,
        List<String> requestedOutputTypes,
        Map<String, String> executionHints) {

    public static ProducerContext of(String executionId, String tenantId, String projectId,
                                       List<String> inputs, List<String> outputs) {
        return new ProducerContext(executionId, tenantId, projectId, inputs, outputs, Map.of());
    }
}
