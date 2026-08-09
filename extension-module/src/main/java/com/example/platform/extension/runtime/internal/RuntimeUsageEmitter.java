package com.example.platform.extension.runtime.internal;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import com.example.platform.billing.usage.UsageUnit;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime-owned canonical usage emission (GAP-002 closure,
 * EVERY_PLUGIN_RUNTIME_EXECUTION_IS_A_GOVERNED_USAGE_PRODUCER).
 *
 * <p>EUMF REUSE ONLY — no second usage system. The runtime emits base facts
 * (REQUEST, DURATION) via {@link UsageRecordEmissionPort}; provider-reported
 * facts (TOKEN_INPUT etc.) are forwarded preserving provenance. Idempotency is
 * anchored on OperationRef + Attempt (same attempt replay → ONE fact; new
 * attempt → distinct fact — PRV2-RED-008/009). FAILED_OPERATION_MAY_STILL_EMIT_USAGE:
 * a failed consumed execution still emits measured facts (PRV2-RED-010).</p>
 */
public final class RuntimeUsageEmitter {

    private final UsageRecordEmissionPort emissionPort;

    public RuntimeUsageEmitter(UsageRecordEmissionPort emissionPort) {
        this.emissionPort = Objects.requireNonNull(emissionPort, "emissionPort must not be null");
    }

    /**
     * Emits runtime base facts (REQUEST + DURATION) for an execution.
     *
     * @param tenantId     tenant (required)
     * @param actorRef     canonical actor (required)
     * @param operationRef operation + attempt (required, idempotency anchor)
     * @param providerRef  provider (required)
     * @param capability   capability (required)
     * @param durationMs   measured duration in ms
     * @return the persisted REQUEST usage record
     */
    public UsageRecord emitBaseFacts(String tenantId, CanonicalActorRef actorRef,
                                     OperationRef operationRef, ProviderRef providerRef,
                                     String capability, long durationMs) {
        Instant now = Instant.now();

        UsageRecord requestRecord = UsageRecord.record(
                tenantId, null, actorRef, operationRef, null, providerRef, capability,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                now, now, now,
                idempotencyKey(operationRef, "REQUEST", "runtime"),
                "REPORTED", "plugin-runtime-v2");

        emissionPort.emit(requestRecord);

        if (durationMs > 0) {
            UsageRecord durationRecord = UsageRecord.record(
                    tenantId, null, actorRef, operationRef, null, providerRef, capability,
                    UsageDimension.DURATION, UsageQuantity.fromBaseUnits(durationMs, UsageUnit.MILLISECONDS),
                    now, now, now,
                    idempotencyKey(operationRef, "DURATION", "runtime"),
                    "REPORTED", "plugin-runtime-v2");
            emissionPort.emit(durationRecord);
        }

        return requestRecord;
    }

    /** Attempt-scoped idempotency key (append-only; replay → one fact). */
    static String idempotencyKey(OperationRef operationRef, String dimension, String source) {
        String attempt = operationRef.attemptId() != null ? operationRef.attemptId() : "no-attempt";
        return "prv2-" + operationRef.operationId() + "-" + attempt + "-" + dimension + "-" + source;
    }
}
