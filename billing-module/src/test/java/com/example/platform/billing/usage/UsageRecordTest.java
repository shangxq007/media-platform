package com.example.platform.billing.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UsageRecordTest {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private static UsageRecord validRecord() {
        return UsageRecord.record(
                "tenant-1",
                "project-1",
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                "capability-1",
                UsageDimension.TOKEN_INPUT,
                UsageQuantity.fromBaseUnits(42, UsageUnit.TOKEN),
                NOW,
                NOW,
                NOW,
                "idem-key-1",
                "REPORTED",
                "unit-test");
    }

    @Test
    void record_producesCompleteRecord() {
        UsageRecord r = validRecord();
        assertNotNull(r.recordId());
        assertEquals("tenant-1", r.tenantId());
        assertEquals("project-1", r.projectId());
        assertNotNull(r.actorRef());
        assertEquals("user-1", r.actorRef().actorId());
        assertEquals("op-1", r.operationRef().operationId());
        assertEquals("attempt-1", r.operationRef().attemptId());
        assertEquals("provider-1", r.providerRef().providerId());
        assertEquals(UsageDimension.TOKEN_INPUT, r.dimension());
        assertEquals(42, r.quantity().baseUnits());
        assertEquals(UsageUnit.TOKEN, r.quantity().unit());
        assertEquals("idem-key-1", r.idempotencyKey());
        assertEquals("REPORTED", r.provenance());
        assertEquals("unit-test", r.source());
    }

    @Test
    void record_generatesUniqueRecordIds() {
        UsageRecord a = validRecord();
        UsageRecord b = validRecord();
        assertNotEquals(a.recordId(), b.recordId());
    }

    @Test
    void record_isImmutableRecord() {
        UsageRecord r = validRecord();
        // Records expose no mutation; verify final-field semantics via reflection-free contract.
        assertEquals("tenant-1", r.tenantId());
        assertThrows(UnsupportedOperationException.class, () -> {
            // Record components are immutable; the set of provenance constants is unmodifiable.
            UsageRecord.VALID_PROVENANCE.add("HACK");
        });
    }

    @Test
    void record_rejectsBlankTenantId() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                "", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "REPORTED", "src"));
    }

    @Test
    void record_rejectsNullTenantId() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                null, null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "REPORTED", "src"));
    }

    @Test
    void record_rejectsNullOperationRef() {
        assertThrows(NullPointerException.class, () -> UsageRecord.record(
                "t", null, null, null, null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "REPORTED", "src"));
    }

    @Test
    void record_rejectsNullQuantity() {
        assertThrows(NullPointerException.class, () -> UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, null,
                null, null, NOW, "k", "REPORTED", "src"));
    }

    @Test
    void record_rejectsBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "  ", "REPORTED", "src"));
    }

    @Test
    void record_rejectsInvalidProvenance() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "GUESSED", "src"));
    }

    @Test
    void record_rejectsBlankSource() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "REPORTED", ""));
    }

    @Test
    void record_rejectsIllegalDimensionUnitPairing() {
        assertThrows(IllegalArgumentException.class, () -> UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.BYTE),
                null, null, NOW, "k", "REPORTED", "src"));
    }

    @Test
    void record_acceptsEstimatedAndDerivedProvenance() {
        UsageRecord estimated = UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.DURATION, UsageQuantity.fromBaseUnits(1000, UsageUnit.MILLISECONDS),
                null, null, NOW, "k-est", "ESTIMATED", "src");
        assertEquals("ESTIMATED", estimated.provenance());

        UsageRecord derived = UsageRecord.record(
                "t", null, null, OperationRef.of("op2"), null, null, null,
                UsageDimension.DURATION, UsageQuantity.fromBaseUnits(2000, UsageUnit.MILLISECONDS),
                null, null, NOW, "k-der", "DERIVED", "src");
        assertEquals("DERIVED", derived.provenance());
    }

    @Test
    void record_allowsNullOptionalFields() {
        UsageRecord r = UsageRecord.record(
                "t", null, null, OperationRef.of("op"), null, null, null,
                UsageDimension.REQUEST, UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT),
                null, null, NOW, "k", "REPORTED", "src");
        assertNull(r.projectId());
        assertNull(r.actorRef());
        assertNull(r.executionRef());
        assertNull(r.providerRef());
        assertNull(r.capability());
        assertNull(r.occurredAt());
        assertNull(r.observedAt());
    }
}
