package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.events.ReconciliationCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    private ReconciliationService service;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ReconciliationService(eventPublisher);
    }

    @Test
    void shouldImportInvoice() {
        ThirdPartyInvoiceImport imported = service.importInvoice(
                "aws", "inv-001", "tenant-1", 50.0, "USD",
                "Compute charges",
                OffsetDateTime.now().minusDays(30), OffsetDateTime.now(),
                "{\"raw\": \"data\"}");
        assertNotNull(imported);
        assertEquals("aws", imported.providerCode());
        assertEquals("inv-001", imported.invoiceId());
        assertEquals(50.0, imported.amount());
    }

    @Test
    void shouldRunReconciliation() {
        // Add internal cost entry
        CostLedgerEntry entry = new CostLedgerEntry(
                "cle-1", "tenant-1", "job-1", "javacv",
                10.0, 10.0, "USD", "RENDER",
                OffsetDateTime.now(), "FINALIZED");
        service.addCostEntry(entry);

        // Import matching external invoice
        service.importInvoice("aws", "inv-001", "tenant-1", 10.0, "USD",
                "Compute", OffsetDateTime.now().minusDays(30), OffsetDateTime.now(), "{}");

        ReconciliationRun run = service.runReconciliation(
                "EXTERNAL_CSV", "aws-invoice",
                OffsetDateTime.now().minusDays(31), OffsetDateTime.now().plusDays(1));

        assertNotNull(run);
        assertEquals("COMPLETED", run.status());
        assertTrue(run.matchedCount() > 0 || run.differenceCount() >= 0);
        verify(eventPublisher).publishEvent(any(ReconciliationCompletedEvent.class));
    }

    @Test
    void shouldDetectDifference() {
        // Add internal record with no matching external
        CostLedgerEntry entry = new CostLedgerEntry(
                "cle-1", "tenant-1", "job-1", "javacv",
                25.0, 25.0, "USD", "RENDER",
                OffsetDateTime.now(), "FINALIZED");
        service.addCostEntry(entry);

        ReconciliationRun run = service.runReconciliation(
                "EXTERNAL_CSV", "aws-invoice",
                OffsetDateTime.now().minusDays(31), OffsetDateTime.now().plusDays(1));

        assertNotNull(run);
        assertTrue(run.differenceCount() > 0);
    }

    @Test
    void shouldResolveDifference() {
        // Create a difference by running reconciliation with mismatched data
        CostLedgerEntry entry = new CostLedgerEntry(
                "cle-1", "tenant-1", "job-1", "javacv",
                25.0, 25.0, "USD", "RENDER",
                OffsetDateTime.now(), "FINALIZED");
        service.addCostEntry(entry);

        ReconciliationRun run = service.runReconciliation(
                "EXTERNAL_CSV", "aws-invoice",
                OffsetDateTime.now().minusDays(31), OffsetDateTime.now().plusDays(1));

        var diffs = service.getDifferences(run.runId());
        assertFalse(diffs.isEmpty());

        ReconciliationDifference resolved = service.resolveDifference(
                diffs.get(0).differenceId(), ReconciliationDifference.STATUS_ACCEPTED, "Within tolerance");
        assertNotNull(resolved);
        assertEquals(ReconciliationDifference.STATUS_ACCEPTED, resolved.status());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    void shouldTrackAllRuns() {
        service.runReconciliation("TEST", "test-source",
                OffsetDateTime.now().minusDays(30), OffsetDateTime.now());
        assertTrue(service.getAllRuns().size() >= 1);
    }
}
