package com.example.platform.observability.app;

import com.example.platform.observability.domain.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.events.ProviderHealthDegradedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors third-party provider health, usage, and SLA metrics.
 */
@Service
public class ThirdPartyProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyProviderHealthService.class);

    private final ConcurrentHashMap<String, ProviderSlaMetric> slaMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderUsageMetric> usageMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderIncidentRecord> incidents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderCircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;
    private final AuditPort auditPort;

    public static final List<String> MONITORED_PROVIDERS = List.of(
            "ai-provider", "s3", "minio", "temporal", "redis", "postgresql",
            "remote-render-worker", "javacv", "ofx", "gpac", "mlt", "gstreamer",
            "payment-provider", "notification-provider");

    public ThirdPartyProviderHealthService(ApplicationEventPublisher eventPublisher,
            AuditPort auditPort) {
        this.eventPublisher = eventPublisher;
        this.auditPort = auditPort;
        initializeProviders();
    }

    private void initializeProviders() {
        for (String provider : MONITORED_PROVIDERS) {
            String type = resolveProviderType(provider);
            circuitBreakers.put(provider, new ProviderCircuitBreakerState(
                    provider, ProviderCircuitBreakerState.STATE_CLOSED,
                    0, 0, 0.0, null, OffsetDateTime.now(), null));
            slaMetrics.put(provider, new ProviderSlaMetric(
                    "sla-" + provider, provider, type,
                    1.0, 0, 0, 0, 0, 1.0, OffsetDateTime.now()));
            usageMetrics.put(provider, new ProviderUsageMetric(
                    "usage-" + provider, provider, type,
                    0, 0, 0, 0, 0, OffsetDateTime.now()));
        }
    }

    private String resolveProviderType(String provider) {
        return switch (provider) {
            case "ai-provider" -> "AI";
            case "s3", "minio" -> "STORAGE";
            case "temporal" -> "WORKFLOW";
            case "redis" -> "CACHE";
            case "postgresql" -> "DATABASE";
            case "remote-render-worker" -> "COMPUTE";
            case "javacv", "ofx", "gpac", "mlt", "gstreamer" -> "RENDER";
            case "payment-provider" -> "PAYMENT";
            case "notification-provider" -> "NOTIFICATION";
            default -> "UNKNOWN";
        };
    }

    /**
     * Record a provider request result.
     */
    public void recordProviderResult(String providerKey, boolean success, long latencyMs, double cost) {
        ProviderCircuitBreakerState cb = circuitBreakers.getOrDefault(providerKey,
                new ProviderCircuitBreakerState(providerKey, ProviderCircuitBreakerState.STATE_CLOSED,
                        0, 0, 0.0, null, OffsetDateTime.now(), null));

        int newFailures = success ? cb.failureCount() : cb.failureCount() + 1;
        int newSuccesses = success ? cb.successCount() + 1 : cb.successCount();
        int total = newFailures + newSuccesses;
        double failureRate = total > 0 ? (double) newFailures / total : 0.0;

        // Circuit breaker logic
        String newState = cb.state();
        OffsetDateTime nextRetry = cb.nextRetryAt();
        if (failureRate > 0.5 && total > 10) {
            newState = ProviderCircuitBreakerState.STATE_OPEN;
            nextRetry = OffsetDateTime.now().plusSeconds(30);
            log.warn("ThirdPartyProviderHealthService: circuit OPEN for provider={} failureRate={}",
                    providerKey, String.format("%.2f", failureRate));
        } else if (ProviderCircuitBreakerState.STATE_OPEN.equals(cb.state())
                && OffsetDateTime.now().isAfter(cb.nextRetryAt() != null ? cb.nextRetryAt() : OffsetDateTime.now())) {
            newState = ProviderCircuitBreakerState.STATE_HALF_OPEN;
        } else if (success && ProviderCircuitBreakerState.STATE_HALF_OPEN.equals(cb.state())) {
            newState = ProviderCircuitBreakerState.STATE_CLOSED;
            nextRetry = null;
        }

        ProviderCircuitBreakerState updated = new ProviderCircuitBreakerState(
                providerKey, newState, newFailures, newSuccesses, failureRate,
                success ? null : OffsetDateTime.now(),
                success ? OffsetDateTime.now() : cb.lastSuccessAt(),
                nextRetry);
        circuitBreakers.put(providerKey, updated);

        // Update SLA metrics
        ProviderSlaMetric currentSla = slaMetrics.getOrDefault(providerKey,
                new ProviderSlaMetric("sla-" + providerKey, providerKey, resolveProviderType(providerKey),
                        1.0, 0, 0, 0, 0, 1.0, OffsetDateTime.now()));
        int newTotal = currentSla.totalRequests() + 1;
        int newFailed = currentSla.failedRequests() + (success ? 0 : 1);
        double newSuccessRate = (double) (newTotal - newFailed) / newTotal;
        ProviderSlaMetric updatedSla = new ProviderSlaMetric(
                currentSla.metricId(), providerKey, currentSla.providerType(),
                newSuccessRate, latencyMs, latencyMs, newTotal, newFailed,
                Math.max(0, currentSla.errorBudgetRemaining() - (success ? 0 : 0.001)),
                OffsetDateTime.now());
        slaMetrics.put(providerKey, updatedSla);

        // Update usage metrics
        ProviderUsageMetric currentUsage = usageMetrics.getOrDefault(providerKey,
                new ProviderUsageMetric("usage-" + providerKey, providerKey, resolveProviderType(providerKey),
                        0, 0, 0, 0, 0, OffsetDateTime.now()));
        ProviderUsageMetric updatedUsage = new ProviderUsageMetric(
                currentUsage.metricId(), providerKey, currentUsage.providerType(),
                currentUsage.requestCount() + 1,
                currentUsage.estimatedCost() + (long) (cost * 100),
                currentUsage.quotaUsed() + 1,
                Math.max(0, currentUsage.quotaRemaining() - 1),
                cost, OffsetDateTime.now());
        usageMetrics.put(providerKey, updatedUsage);

        // Emit event if unhealthy
        if (!updatedSla.isHealthy()) {
            eventPublisher.publishEvent(new ProviderHealthDegradedEvent(
                    providerKey, resolveProviderType(providerKey),
                    updatedSla.healthStatus(), updatedSla.successRate() * 100,
                    (long) updatedSla.avgLatencyMs(), "SLA below threshold",
                    java.time.Instant.now()));
        }
    }

    /**
     * Report a provider incident.
     */
    public ProviderIncidentRecord reportIncident(String providerKey, String severity,
            String title, String description) {
        ProviderIncidentRecord incident = new ProviderIncidentRecord(
                java.util.UUID.randomUUID().toString(),
                providerKey, resolveProviderType(providerKey),
                severity, title, description,
                ProviderIncidentRecord.STATUS_OPEN,
                OffsetDateTime.now(), null);
        incidents.put(incident.incidentId(), incident);
        auditPort.record("system", "INCIDENT_REPORTED", "PROVIDER_HEALTH",
                "provider_incident", incident.incidentId(),
                Map.of("provider", providerKey, "severity", severity));
        log.warn("ThirdPartyProviderHealthService: incident reported for provider={}: {}", providerKey, title);
        return incident;
    }

    /**
     * Resolve an incident.
     */
    public ProviderIncidentRecord resolveIncident(String incidentId) {
        ProviderIncidentRecord existing = incidents.get(incidentId);
        if (existing == null) return null;
        ProviderIncidentRecord resolved = new ProviderIncidentRecord(
                existing.incidentId(), existing.providerKey(), existing.providerType(),
                existing.severity(), existing.title(), existing.description(),
                ProviderIncidentRecord.STATUS_RESOLVED,
                existing.startedAt(), OffsetDateTime.now());
        incidents.put(incidentId, resolved);
        return resolved;
    }

    /**
     * Get overall health summary for all monitored providers.
     */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String provider : MONITORED_PROVIDERS) {
            ProviderSlaMetric sla = slaMetrics.get(provider);
            ProviderCircuitBreakerState cb = circuitBreakers.get(provider);
            ProviderUsageMetric usage = usageMetrics.get(provider);
            Map<String, Object> providerInfo = new LinkedHashMap<>();
            if (sla != null) {
                providerInfo.put("healthStatus", sla.healthStatus());
                providerInfo.put("successRate", String.format("%.2f%%", sla.successRate() * 100));
                providerInfo.put("avgLatencyMs", sla.avgLatencyMs());
            }
            if (cb != null) {
                providerInfo.put("circuitState", cb.state());
                providerInfo.put("failureRate", String.format("%.2f%%", cb.failureRate() * 100));
            }
            if (usage != null) {
                providerInfo.put("requestCount", usage.requestCount());
                providerInfo.put("estimatedCost", usage.estimatedCost() / 100.0);
            }
            summary.put(provider, providerInfo);
        }
        return summary;
    }

    public ProviderSlaMetric getSlaMetric(String providerKey) { return slaMetrics.get(providerKey); }
    public ProviderUsageMetric getUsageMetric(String providerKey) { return usageMetrics.get(providerKey); }
    public ProviderCircuitBreakerState getCircuitBreaker(String providerKey) { return circuitBreakers.get(providerKey); }
    public List<ProviderIncidentRecord> getActiveIncidents() {
        return incidents.values().stream()
                .filter(i -> !ProviderIncidentRecord.STATUS_RESOLVED.equals(i.status()))
                .toList();
    }
}
