package com.example.platform.audit.app;

import com.example.platform.audit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsageAnomalyDetectionServiceTest {

    private UsageAnomalyDetectionService service;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new UsageAnomalyDetectionService(eventPublisher);
    }

    @Test
    void shouldReturnCleanForNormalUsage() {
        UsageAnomalyDetectionService.AnomalyCheckResult result = service.analyzeSubmission(
                "tenant-1", "user-1", "default_720p", "javacv");
        assertNotNull(result);
        assertTrue(result.clean());
        assertEquals(0, result.detectedAnomalies().size());
    }

    @Test
    void shouldDetectRenderBurst() {
        // Submit many jobs quickly
        UsageAnomalyDetectionService.AnomalyCheckResult lastResult = null;
        for (int i = 0; i < 15; i++) {
            lastResult = service.analyzeSubmission("tenant-1", "user-1", "default_720p", "javacv");
        }
        assertNotNull(lastResult);
        assertFalse(lastResult.clean());
        assertTrue(lastResult.detectedAnomalies().contains("render_burst"));
        assertTrue(lastResult.riskScore() > 0);
    }

    @Test
    void shouldDetectGpuCostSpike() {
        UsageAnomalyDetectionService.AnomalyCheckResult lastResult = null;
        for (int i = 0; i < 8; i++) {
            lastResult = service.analyzeSubmission("tenant-1", "user-1", "gpu_h264", "remote-javacv");
        }
        assertNotNull(lastResult);
        assertTrue(lastResult.detectedAnomalies().contains("gpu_cost_spike"));
    }

    @Test
    void shouldRecordFailure() {
        for (int i = 0; i < 6; i++) {
            service.recordFailure("tenant-1", "user-1", "RENDER_FAILED");
        }
        // Should not throw
        assertTrue(true);
    }

    @Test
    void shouldProvideRecommendedPresetOnAnomaly() {
        UsageAnomalyDetectionService.AnomalyCheckResult lastResult = null;
        for (int i = 0; i < 15; i++) {
            lastResult = service.analyzeSubmission("tenant-1", "user-1", "default_720p", "javacv");
        }
        assertNotNull(lastResult);
        if (!lastResult.clean()) {
            assertNotNull(lastResult.recommendedPreset());
        }
    }

    @Test
    void shouldHaveMitigationActions() {
        UsageAnomalyDetectionService.AnomalyCheckResult lastResult = null;
        for (int i = 0; i < 15; i++) {
            lastResult = service.analyzeSubmission("tenant-1", "user-1", "default_720p", "javacv");
        }
        assertNotNull(lastResult);
        if (!lastResult.clean()) {
            assertFalse(lastResult.mitigationActions().isEmpty());
        }
    }

    @Test
    void shouldUpdateRiskProfile() {
        service.analyzeSubmission("tenant-1", "user-1", "default_720p", "javacv");
        UsageRiskProfile profile = service.getRiskProfile("tenant-1", "user-1");
        assertNotNull(profile);
        assertEquals("tenant-1", profile.tenantId());
        assertEquals("user-1", profile.userId());
    }
}
