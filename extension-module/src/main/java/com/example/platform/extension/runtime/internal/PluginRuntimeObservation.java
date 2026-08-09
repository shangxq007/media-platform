package com.example.platform.extension.runtime.internal;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime execution observation record (PLUGIN_RUNTIME_OBSERVABILITY_MODEL_V1).
 *
 * <p>Observability events are Metric-like records — they are NEVER emitted as
 * UsageRecord (Metric != Trace != Log != UsageRecord, AR-PRV2-09 note).</p>
 *
 * @param operationId          logical operation id
 * @param attemptId            attempt id (nullable)
 * @param providerId           provider id
 * @param capability           capability
 * @param executionMode        execution mode name
 * @param latencyMs            execution latency
 * @param status               terminal status
 * @param errorCategory        canonical error category (nullable when success)
 * @param providerOperationId  provider-side operation id (nullable)
 * @param usageRecordId        correlated usage record id (nullable)
 * @param costObservationId    correlated cost observation id (nullable)
 * @param traceId              trace correlation id (nullable)
 * @param observedAt           observation timestamp
 */
public record PluginRuntimeObservation(
        String operationId,
        String attemptId,
        String providerId,
        String capability,
        String executionMode,
        long latencyMs,
        String status,
        String errorCategory,
        String providerOperationId,
        String usageRecordId,
        String costObservationId,
        String traceId,
        Instant observedAt) {

    public PluginRuntimeObservation {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(providerId, "providerId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (observedAt == null) {
            observedAt = Instant.now();
        }
    }
}
