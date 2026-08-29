package com.example.platform.shared.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ObservedRuntimeUsageTest {

    private static final Instant OCCURRED = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-08-29T08:00:01Z");
    private static final Instant RECORDED = Instant.parse("2026-08-29T08:00:02Z");

    @Test
    void observationCarriesOnlyImmutableOperationalTruth() {
        ObservedRuntimeUsage usage = observation("attempt-1", RuntimeOutcome.FAILED, "idem-1");

        assertEquals("tenant-a", usage.tenantId());
        assertEquals("user-a", usage.principalRef().actorId());
        assertEquals("attempt-1", usage.operationRef().attemptId());
        assertEquals(RuntimeOutcome.FAILED, usage.outcome());
        assertEquals(1_501L, usage.quantity().baseUnits());
        assertEquals(OCCURRED, usage.occurredAt());
        assertEquals(OBSERVED, usage.observedAt());
        assertEquals(RECORDED, usage.recordedAt());
    }

    @Test
    void operationAttemptAndStableIdempotencyAreMandatory() {
        assertThrows(IllegalArgumentException.class,
                () -> observation(null, RuntimeOutcome.SUCCEEDED, "idem-1"));
        assertThrows(IllegalArgumentException.class,
                () -> observation("attempt-1", RuntimeOutcome.SUCCEEDED, " "));
    }

    @Test
    void aNewAttemptIsASeparateObservation() {
        ObservedRuntimeUsage first = observation("attempt-1", RuntimeOutcome.SUCCEEDED, "idem-1");
        ObservedRuntimeUsage retry = observation("attempt-2", RuntimeOutcome.SUCCEEDED, "idem-2");

        assertNotEquals(first.operationRef().attemptId(), retry.operationRef().attemptId());
        assertNotEquals(first.idempotencyKey(), retry.idempotencyKey());
    }

    @Test
    void dimensionAndBaseUnitMustBeExplicitAndCompatible() {
        assertThrows(NullPointerException.class, () -> new UsageQuantity(1, null));
        assertThrows(IllegalArgumentException.class, () -> ObservedRuntimeUsage.observe(
                "tenant-a", "project-a", new CanonicalActorRef("user-a", "USER"),
                OperationRef.of("operation-a", "attempt-1"), "execution-a",
                new ProviderRef("provider-a"), "capability-a", UsageDimension.DURATION,
                new UsageQuantity(1, UsageUnit.TOKEN), RuntimeOutcome.SUCCEEDED,
                OCCURRED, OBSERVED, RECORDED, UsageProvenance.REPORTED,
                "runtime", "source-a", "trace-a", "idem-a"));
    }

    private static ObservedRuntimeUsage observation(
            String attemptId, RuntimeOutcome outcome, String idempotencyKey) {
        return ObservedRuntimeUsage.observe(
                "tenant-a",
                "project-a",
                new CanonicalActorRef("user-a", "USER"),
                OperationRef.of("operation-a", attemptId),
                "execution-a",
                new ProviderRef("provider-a"),
                "capability-a",
                UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(1_501L, UsageUnit.MILLISECONDS),
                outcome,
                OCCURRED,
                OBSERVED,
                RECORDED,
                UsageProvenance.REPORTED,
                "runtime",
                "source-a",
                "trace-a",
                idempotencyKey);
    }
}
