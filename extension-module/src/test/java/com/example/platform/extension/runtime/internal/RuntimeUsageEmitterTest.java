package com.example.platform.extension.runtime.internal;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageRecordEmissionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeUsageEmitterTest {

    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("u-1", "USER");
    private static final ProviderRef PROVIDER = new ProviderRef("provider-a");

    /** In-memory fake emission port (unit tests must not need a real DB). */
    static final class FakeEmissionPort implements UsageRecordEmissionPort {
        final List<UsageRecord> emitted = new ArrayList<>();

        @Override
        public UsageRecord emit(UsageRecord record) {
            emitted.add(record);
            return record;
        }
    }

    @Test
    void emitsRequestAndDurationFacts() {
        FakeEmissionPort port = new FakeEmissionPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        OperationRef op = OperationRef.of("op-1", "attempt-1");

        emitter.emitBaseFacts("tenant-1", ACTOR, op, PROVIDER, "cap-stt", 1500);

        assertEquals(2, port.emitted.size());
        UsageRecord request = port.emitted.get(0);
        UsageRecord duration = port.emitted.get(1);
        assertEquals(UsageDimension.REQUEST, request.dimension());
        assertEquals(UsageDimension.DURATION, duration.dimension());
        assertEquals("tenant-1", request.tenantId());
        assertEquals("provider-a", request.providerRef().providerId());
        assertEquals("REPORTED", request.provenance());
        assertEquals("plugin-runtime-v2", request.source());
        assertTrue(request.idempotencyKey().startsWith("prv2-op-1-attempt-1-"));
    }

    @Test
    void sameAttemptReplayProducesSameIdempotencyKey() {
        OperationRef op = OperationRef.of("op-1", "attempt-1");
        assertEquals(
                RuntimeUsageEmitter.idempotencyKey(op, "DURATION", "runtime"),
                RuntimeUsageEmitter.idempotencyKey(op, "DURATION", "runtime"));
    }

    @Test
    void newAttemptProducesDistinctIdempotencyKey() {
        OperationRef attempt1 = OperationRef.of("op-1", "attempt-1");
        OperationRef attempt2 = OperationRef.of("op-1", "attempt-2");
        String k1 = RuntimeUsageEmitter.idempotencyKey(attempt1, "DURATION", "runtime");
        String k2 = RuntimeUsageEmitter.idempotencyKey(attempt2, "DURATION", "runtime");
        assertTrue(!k1.equals(k2));
    }

    @Test
    void zeroDurationEmitsRequestOnly() {
        FakeEmissionPort port = new FakeEmissionPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        emitter.emitBaseFacts("tenant-1", ACTOR, OperationRef.of("op-2", "attempt-1"),
                PROVIDER, "cap-1", 0);
        assertEquals(1, port.emitted.size());
        assertEquals(UsageDimension.REQUEST, port.emitted.get(0).dimension());
    }

    @Test
    void failedConsumedExecutionStillEmits() {
        // FAILED_OPERATION_MAY_STILL_EMIT_USAGE (PRV2-RED-010): the emitter does not
        // suppress facts; a failed execution with measured duration still emits.
        FakeEmissionPort port = new FakeEmissionPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        OperationRef op = OperationRef.of("op-fail", "attempt-1");
        // Simulate failed-but-consumed: duration measured, facts emitted regardless
        emitter.emitBaseFacts("tenant-1", ACTOR, op, PROVIDER, "cap-1", 500);
        assertEquals(2, port.emitted.size());
        assertTrue(port.emitted.stream().anyMatch(r -> r.dimension() == UsageDimension.DURATION));
    }
}
