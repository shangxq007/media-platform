package com.example.platform.extension.runtime.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.ObservedRuntimeUsageEmissionPort;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.usage.RuntimeOutcome;
import com.example.platform.shared.usage.UsageDimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeObservedUsageEmitterTest {

    private static final Instant OCCURRED = Instant.parse("2026-08-29T08:00:00Z");
    private static final CanonicalActorRef ACTOR = new CanonicalActorRef("user-a", "USER");
    private static final ProviderRef PROVIDER = new ProviderRef("provider-a");

    @Test
    void emitsNeutralRequestAndDurationObservations() {
        CapturingPort port = new CapturingPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);

        emitter.emitBaseFacts(
                "tenant-a", ACTOR, OperationRef.of("operation-a", "attempt-1"),
                PROVIDER, "capability-a", 1_501, RuntimeOutcome.SUCCEEDED,
                OCCURRED, "trace-a");

        assertEquals(2, port.observations.size());
        assertEquals(UsageDimension.REQUEST, port.observations.get(0).dimension());
        assertEquals(UsageDimension.DURATION, port.observations.get(1).dimension());
        assertEquals("tenant-a", port.observations.get(0).tenantId());
        assertEquals("attempt-1", port.observations.get(0).operationRef().attemptId());
        assertEquals("provider-a", port.observations.get(0).providerRef().providerId());
    }

    @Test
    void sameAttemptReplayHasStableSemanticPayloadAndKeys() {
        CapturingPort port = new CapturingPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);
        OperationRef attempt = OperationRef.of("operation-a", "attempt-1");

        emitter.emitBaseFacts("tenant-a", ACTOR, attempt, PROVIDER, "capability-a", 500,
                RuntimeOutcome.SUCCEEDED, OCCURRED, "trace-a");
        emitter.emitBaseFacts("tenant-a", ACTOR, attempt, PROVIDER, "capability-a", 500,
                RuntimeOutcome.SUCCEEDED, OCCURRED, "trace-a");

        assertEquals(port.observations.get(0).idempotencyKey(),
                port.observations.get(2).idempotencyKey());
        assertEquals(port.observations.get(1).idempotencyKey(),
                port.observations.get(3).idempotencyKey());
        assertEquals(port.observations.get(0).occurredAt(), port.observations.get(2).occurredAt());
        assertEquals(port.observations.get(0).recordedAt(), port.observations.get(2).recordedAt());
    }

    @Test
    void newAttemptProducesDistinctObservations() {
        CapturingPort port = new CapturingPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);

        emitter.emitBaseFacts("tenant-a", ACTOR,
                OperationRef.of("operation-a", "attempt-1"), PROVIDER, "capability-a", 500,
                RuntimeOutcome.SUCCEEDED, OCCURRED, "trace-a");
        emitter.emitBaseFacts("tenant-a", ACTOR,
                OperationRef.of("operation-a", "attempt-2"), PROVIDER, "capability-a", 500,
                RuntimeOutcome.SUCCEEDED, OCCURRED, "trace-a");

        assertNotEquals(port.observations.get(0).idempotencyKey(),
                port.observations.get(2).idempotencyKey());
    }

    @Test
    void failedButConsumedAttemptEmitsObservedUsage() {
        CapturingPort port = new CapturingPort();
        RuntimeUsageEmitter emitter = new RuntimeUsageEmitter(port);

        emitter.emitBaseFacts("tenant-a", ACTOR,
                OperationRef.of("operation-a", "attempt-failed"), PROVIDER, "capability-a", 800,
                RuntimeOutcome.FAILED, OCCURRED, "trace-failed");

        assertEquals(2, port.observations.size());
        assertEquals(RuntimeOutcome.FAILED, port.observations.get(0).outcome());
        assertEquals(RuntimeOutcome.FAILED, port.observations.get(1).outcome());
    }

    private static final class CapturingPort implements ObservedRuntimeUsageEmissionPort {
        private final List<ObservedRuntimeUsage> observations = new ArrayList<>();

        @Override
        public ObservedRuntimeUsage emit(ObservedRuntimeUsage observation) {
            observations.add(observation);
            return observation;
        }
    }
}
