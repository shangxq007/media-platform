package com.example.platform.extension.runtime.internal;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.ObservedRuntimeUsageEmissionPort;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.usage.RuntimeOutcome;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageProvenance;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.time.Instant;
import java.util.Objects;

/** Runtime-owned producer of neutral, immutable usage observations. */
public final class RuntimeUsageEmitter {

    private final ObservedRuntimeUsageEmissionPort emissionPort;

    public RuntimeUsageEmitter(ObservedRuntimeUsageEmissionPort emissionPort) {
        this.emissionPort = Objects.requireNonNull(emissionPort, "emissionPort must not be null");
    }

    /** Emits REQUEST and, when measured, DURATION facts for one operation attempt. */
    public ObservedRuntimeUsage emitBaseFacts(
            String tenantId,
            CanonicalActorRef actorRef,
            OperationRef operationRef,
            ProviderRef providerRef,
            String capability,
            long durationMs,
            RuntimeOutcome outcome,
            Instant occurredAt,
            String traceId) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        String sourceReference = providerRef.providerId() + ":"
                + operationRef.operationId() + ":" + operationRef.attemptId();

        ObservedRuntimeUsage request = observation(
                tenantId, actorRef, operationRef, providerRef, capability,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                outcome, occurredAt, traceId, sourceReference);
        emissionPort.emit(request);

        if (durationMs > 0) {
            emissionPort.emit(observation(
                    tenantId, actorRef, operationRef, providerRef, capability,
                    UsageDimension.DURATION,
                    UsageQuantity.fromBaseUnits(durationMs, UsageUnit.MILLISECONDS),
                    outcome, occurredAt, traceId, sourceReference));
        }
        return request;
    }

    private static ObservedRuntimeUsage observation(
            String tenantId,
            CanonicalActorRef actorRef,
            OperationRef operationRef,
            ProviderRef providerRef,
            String capability,
            UsageDimension dimension,
            UsageQuantity quantity,
            RuntimeOutcome outcome,
            Instant occurredAt,
            String traceId,
            String sourceReference) {
        return ObservedRuntimeUsage.observe(
                tenantId, null, actorRef, operationRef, null, providerRef, capability,
                dimension, quantity, outcome, occurredAt, occurredAt, occurredAt,
                UsageProvenance.REPORTED, "plugin-runtime-v2", sourceReference, traceId,
                idempotencyKey(operationRef, dimension.name(), "runtime"));
    }

    static String idempotencyKey(OperationRef operationRef, String dimension, String source) {
        String attempt = operationRef.attemptId() != null ? operationRef.attemptId() : "no-attempt";
        return "prv2-" + operationRef.operationId() + "-" + attempt + "-" + dimension + "-" + source;
    }
}
