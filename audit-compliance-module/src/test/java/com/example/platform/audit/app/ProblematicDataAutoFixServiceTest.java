package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblematicDataAutoFixServiceTest {

    private AuditService auditService;
    private ProblematicDataAutoFixService service;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        service = new ProblematicDataAutoFixService(auditService);
    }

    @Test
    void shouldAutoFixMissingField() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.MISSING_FIELD,
                ProblematicSeverity.HIGH, false);

        var fixed = service.attemptAutoFix(record);

        assertEquals(ProblematicDataStatus.AUTO_FIXED, fixed.status());
        assertNotNull(fixed.autoFixApplied());
        verify(auditService).record(eq("SYSTEM"), eq("problematic-data-autofix"),
                eq("PROBLEMATIC_DATA_AUTO_FIXED"), eq("problematic_data"), anyString(), any(Map.class));
    }

    @Test
    void shouldAutoFixDuplicateEntry() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.DUPLICATE_ENTRY,
                ProblematicSeverity.LOW, true);

        var fixed = service.attemptAutoFix(record);

        assertEquals(ProblematicDataStatus.AUTO_FIXED, fixed.status());
    }

    @Test
    void shouldAutoFixStuckJob() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.INVALID_STATE_TRANSITION,
                ProblematicSeverity.MEDIUM, true);

        var fixed = service.attemptAutoFix(record);

        assertEquals(ProblematicDataStatus.AUTO_FIXED, fixed.status());
    }

    @Test
    void shouldAutoFixPerformanceAnomaly() {
        var record = createRecord("PROVIDER_WORKER", ProblematicDataType.PERFORMANCE_ANOMALY,
                ProblematicSeverity.MEDIUM, true);

        var fixed = service.attemptAutoFix(record);

        assertEquals(ProblematicDataStatus.AUTO_FIXED, fixed.status());
    }

    @Test
    void shouldNotFixNonAutoFixableIssues() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.SLA_BREACH,
                ProblematicSeverity.CRITICAL, false);

        var result = service.attemptAutoFix(record);

        assertEquals(ProblematicDataStatus.DETECTED, result.status());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    void shouldQuarantineCriticalIssues() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.SLA_BREACH,
                ProblematicSeverity.CRITICAL, false);

        var quarantined = service.quarantine(record, "quarantined_render_jobs");

        assertEquals(ProblematicDataStatus.QUARANTINED, quarantined.status());
        verify(auditService).record(eq("SYSTEM"), eq("problematic-data-autofix"),
                eq("PROBLEMATIC_DATA_QUARANTINED"), eq("problematic_data"), anyString(), any(Map.class));
    }

    @Test
    void shouldBatchProcessMultipleRecords() {
        var records = List.of(
                createRecord("RENDER_JOB", ProblematicDataType.MISSING_FIELD, ProblematicSeverity.HIGH, true),
                createRecord("RENDER_JOB", ProblematicDataType.SLA_BREACH, ProblematicSeverity.CRITICAL, false),
                createRecord("PROMPT_EXECUTION", ProblematicDataType.OUTPUT_MISMATCH, ProblematicSeverity.HIGH, false)
        );

        var result = service.batchProcess(records);

        assertEquals(3, result.totalDetected());
        assertEquals(1, result.autoFixed());
        assertEquals(1, result.quarantined());
        assertEquals(1, result.humanReviewRequired());
    }

    @Test
    void shouldHandleFixFailureGracefully() {
        var record = createRecord("RENDER_JOB", ProblematicDataType.COST_ANOMALY,
                ProblematicSeverity.HIGH, false);

        var result = service.attemptAutoFix(record);

        // Should not throw, should mark for human review
        assertTrue(result.requiresHumanReview() || result.status() == ProblematicDataStatus.DETECTED);
    }

    private ProblematicDataRecord createRecord(String dataType, ProblematicDataType problematicType,
            ProblematicSeverity severity, boolean autoFixable) {
        return new ProblematicDataRecord(
                "pd-test-" + System.nanoTime(), dataType, "data-1", "tenant-1", "user-1",
                problematicType, severity, "TEST-001", "Test description",
                Map.of("test", true), null, null, null, null, null,
                ProblematicDataStatus.DETECTED, null, null,
                !autoFixable, null, OffsetDateTime.now(), null, null);
    }
}
