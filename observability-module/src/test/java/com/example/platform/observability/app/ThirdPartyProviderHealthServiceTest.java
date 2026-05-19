package com.example.platform.observability.app;

import com.example.platform.observability.domain.*;
import com.example.platform.shared.audit.AuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThirdPartyProviderHealthServiceTest {

    private ThirdPartyProviderHealthService service;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        AuditPort auditPort = mock(AuditPort.class);
        service = new ThirdPartyProviderHealthService(eventPublisher, auditPort);
    }

    @Test
    void shouldInitializeAllMonitoredProviders() {
        for (String provider : ThirdPartyProviderHealthService.MONITORED_PROVIDERS) {
            ProviderSlaMetric sla = service.getSlaMetric(provider);
            assertNotNull(sla, "SLA metric should exist for " + provider);
            ProviderCircuitBreakerState cb = service.getCircuitBreaker(provider);
            assertNotNull(cb, "Circuit breaker should exist for " + provider);
        }
    }

    @Test
    void shouldRecordSuccessfulRequest() {
        service.recordProviderResult("javacv", true, 100, 0.01);
        ProviderSlaMetric sla = service.getSlaMetric("javacv");
        assertNotNull(sla);
        assertEquals(1.0, sla.successRate());
        assertEquals("HEALTHY", sla.healthStatus());
    }

    @Test
    void shouldRecordFailedRequest() {
        for (int i = 0; i < 15; i++) {
            service.recordProviderResult("javacv", false, 5000, 0.01);
        }
        ProviderSlaMetric sla = service.getSlaMetric("javacv");
        assertNotNull(sla);
        assertTrue(sla.successRate() < 0.5);
    }

    @Test
    void shouldOpenCircuitAfterFailures() {
        for (int i = 0; i < 20; i++) {
            service.recordProviderResult("javacv", false, 5000, 0.01);
        }
        ProviderCircuitBreakerState cb = service.getCircuitBreaker("javacv");
        assertNotNull(cb);
        assertTrue(cb.isCircuitOpen());
    }

    @Test
    void shouldUpdateUsageMetrics() {
        service.recordProviderResult("javacv", true, 100, 0.05);
        service.recordProviderResult("javacv", true, 200, 0.03);
        ProviderUsageMetric usage = service.getUsageMetric("javacv");
        assertNotNull(usage);
        assertEquals(2, usage.requestCount());
    }

    @Test
    void shouldReportIncident() {
        ProviderIncidentRecord incident = service.reportIncident(
                "javacv", ProviderIncidentRecord.SEVERITY_MAJOR,
                "High latency detected", "P99 latency exceeded 5s");
        assertNotNull(incident);
        assertEquals("javacv", incident.providerKey());
        assertEquals(ProviderIncidentRecord.STATUS_OPEN, incident.status());
    }

    @Test
    void shouldResolveIncident() {
        ProviderIncidentRecord incident = service.reportIncident(
                "javacv", ProviderIncidentRecord.SEVERITY_MINOR,
                "Brief outage", "Service briefly unavailable");
        ProviderIncidentRecord resolved = service.resolveIncident(incident.incidentId());
        assertNotNull(resolved);
        assertEquals(ProviderIncidentRecord.STATUS_RESOLVED, resolved.status());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    void shouldGetActiveIncidents() {
        service.recordProviderResult("javacv", true, 100, 0.01);
        var active = service.getActiveIncidents();
        assertNotNull(active);
    }

    @Test
    void shouldGetHealthSummary() {
        service.recordProviderResult("javacv", true, 100, 0.01);
        var summary = service.getHealthSummary();
        assertNotNull(summary);
        assertTrue(summary.containsKey("javacv"));
    }

    @Test
    void shouldNotEmitEventForHealthyProvider() {
        service.recordProviderResult("javacv", true, 100, 0.01);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
