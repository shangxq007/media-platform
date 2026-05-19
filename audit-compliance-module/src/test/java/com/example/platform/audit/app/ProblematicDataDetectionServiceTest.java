package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import com.example.platform.shared.events.ProblematicDataDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblematicDataDetectionServiceTest {

    private AuditService auditService;
    private ApplicationEventPublisher eventPublisher;
    private ProblematicDataDetectionService service;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ProblematicDataDetectionService(auditService, eventPublisher);
    }

    @Test
    void shouldDetectMissingRenderJobOutput() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "artifactCount", 0);

        var detected = service.detectRenderJobIssues("job-1", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.MISSING_FIELD));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(ProblematicDataDetectedEvent.class));
    }

    @Test
    void shouldDetectStuckRenderJob() {
        Map<String, Object> data = Map.of(
                "status", "PROCESSING",
                "minutesInState", 45L);

        var detected = service.detectRenderJobIssues("job-2", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.INVALID_STATE_TRANSITION));
    }

    @Test
    void shouldDetectCostAnomaly() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "actualCost", 10.0,
                "estimatedCost", 1.0);

        var detected = service.detectRenderJobIssues("job-3", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.COST_ANOMALY));
    }

    @Test
    void shouldDetectSlaBreach() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "processingTimeSeconds", 3600L,
                "slaThresholdSeconds", 1800L);

        var detected = service.detectRenderJobIssues("job-4", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.SLA_BREACH));
        assertTrue(detected.stream().anyMatch(r -> r.severity() == ProblematicSeverity.CRITICAL));
    }

    @Test
    void shouldDetectSensitiveDataLeak() {
        Map<String, Object> data = Map.of(
                "status", "SUCCEEDED",
                "inputVariablesRedactedJson", "{\"password\": \"secret123\", \"name\": \"World\"}");

        var detected = service.detectPromptExecutionIssues("exec-1", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.MISSING_FIELD));
        assertTrue(detected.stream().anyMatch(r -> r.severity() == ProblematicSeverity.CRITICAL));
    }

    @Test
    void shouldDetectPromptOutputMismatch() {
        Map<String, Object> data = Map.of(
                "status", "SUCCEEDED",
                "outputSummary", "");

        var detected = service.detectPromptExecutionIssues("exec-2", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.OUTPUT_MISMATCH));
    }

    @Test
    void shouldDetectProviderErrorSpike() {
        Map<String, Object> data = Map.of("errorRate", 0.3);

        var detected = service.detectProviderWorkerIssues("javacv", null, "tenant-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.ERROR_RATE_SPIKE));
    }

    @Test
    void shouldDetectWorkerStaleHeartbeat() {
        Map<String, Object> data = Map.of("minutesSinceLastHeartbeat", 10L);

        var detected = service.detectProviderWorkerIssues(null, "worker-1", "tenant-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(r -> r.problematicType() == ProblematicDataType.PERFORMANCE_ANOMALY));
    }

    @Test
    void shouldReturnNoIssuesForHealthyData() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "artifactCount", 1,
                "actualCost", 1.0,
                "estimatedCost", 1.0,
                "processingTimeSeconds", 100L,
                "slaThresholdSeconds", 1800L);

        var detected = service.detectRenderJobIssues("job-ok", "tenant-1", "user-1", data);

        assertTrue(detected.isEmpty());
    }

    @Test
    void shouldReturnActiveRules() {
        var rules = service.getActiveRules();
        assertFalse(rules.isEmpty());
        assertTrue(rules.size() >= 9);
    }

    @Test
    void shouldMarkCriticalIssuesForHumanReview() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "processingTimeSeconds", 3600L,
                "slaThresholdSeconds", 1800L);

        var detected = service.detectRenderJobIssues("job-5", "tenant-1", "user-1", data);

        assertFalse(detected.isEmpty());
        assertTrue(detected.stream().anyMatch(ProblematicDataRecord::requiresHumanReview));
    }

    @Test
    void shouldWriteAuditForDetectedIssues() {
        Map<String, Object> data = Map.of(
                "status", "COMPLETED",
                "artifactCount", 0);

        service.detectRenderJobIssues("job-6", "tenant-1", "user-1", data);

        verify(auditService, atLeastOnce()).record(
                eq("system"), eq("PROBLEMATIC_DATA_DETECTED"), eq("AUDIT"),
                eq("problematic_data"), anyString(), any(Map.class));
    }
}
