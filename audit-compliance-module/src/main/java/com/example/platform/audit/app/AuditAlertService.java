package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Evaluates audit records for security alert conditions.
 *
 * <p>Alert rules:
 * <ul>
 *   <li>ADMIN_AUDIT + result=DENIED → HIGH severity (single event)</li>
 *   <li>ADMIN_AUDIT + result=FAILED → MEDIUM severity (single event)</li>
 *   <li>UNKNOWN + result=DENIED → MEDIUM severity (single event)</li>
 *   <li>Same actorId + ADMIN_AUDIT + DENIED × threshold within window → ADMIN_DENIED_BURST HIGH (aggregated)</li>
 * </ul>
 *
 * <p>Alerts are published through {@link SecurityAlertPort}.
 * Alert evaluation MUST NOT block the business flow.
 */
@Service
public class AuditAlertService {

    private static final Logger log = LoggerFactory.getLogger(AuditAlertService.class);

    private final AuditAlertProperties properties;
    private final SecurityAlertPort alertPublisher;
    private final Clock clock;
    private final ConcurrentHashMap<String, ActorDeniedWindow> actorWindows = new ConcurrentHashMap<>();

    public AuditAlertService(AuditAlertProperties properties, SecurityAlertPort alertPublisher) {
        this(properties, alertPublisher, Clock.systemUTC());
    }

    // Visible for testing
    AuditAlertService(AuditAlertProperties properties, SecurityAlertPort alertPublisher, Clock clock) {
        this.properties = properties;
        this.alertPublisher = alertPublisher;
        this.clock = clock;
    }

    /**
     * Evaluate an audit record for alert conditions.
     */
    public void evaluate(String category, String action, String actorType, String actorId,
                         String resourceType, String resourceId, String targetTenantId,
                         String result, String requestId, String traceId) {
        try {
            if (category == null || result == null) {
                return;
            }

            // Rule 1: ADMIN_AUDIT + DENIED → single HIGH alert
            if ("ADMIN_AUDIT".equals(category) && "DENIED".equals(result)) {
                publishAlert(SecurityAlert.of(
                        "SINGLE_DENIED", "HIGH", category, action,
                        actorType, actorId, resourceType, resourceId,
                        targetTenantId, result, requestId, traceId));

                // Also feed into burst detection
                evaluateBurst(actorId, action, targetTenantId, requestId, traceId);
                return;
            }

            // Rule 2: ADMIN_AUDIT + FAILED → MEDIUM
            if ("ADMIN_AUDIT".equals(category) && "FAILED".equals(result)) {
                publishAlert(SecurityAlert.of(
                        "SINGLE_FAILED", "MEDIUM", category, action,
                        actorType, actorId, resourceType, resourceId,
                        targetTenantId, result, requestId, traceId));
                return;
            }

            // Rule 3: UNKNOWN category + DENIED → MEDIUM
            if ("UNKNOWN".equals(category) && "DENIED".equals(result)) {
                publishAlert(SecurityAlert.of(
                        "SINGLE_DENIED_UNKNOWN", "MEDIUM", category, action,
                        actorType, actorId, resourceType, resourceId,
                        targetTenantId, result, requestId, traceId));
            }

        } catch (Exception e) {
            // Alert evaluation MUST NOT block business flow
            log.warn("AuditAlertService evaluation failed: {}", e.getMessage());
        }
    }

    // ==================== Burst detection ====================

    private void evaluateBurst(String actorId, String action, String targetTenantId,
                               String requestId, String traceId) {
        AuditAlertProperties.DeniedBurst config = properties.deniedBurst();
        if (!config.enabled()) {
            return;
        }

        // Skip if actorId is empty — can't aggregate anonymous
        if (actorId == null || actorId.isBlank()) {
            return;
        }

        // Prevent memory exhaustion
        if (actorWindows.size() >= config.maxActors()) {
            evictStaleWindows(config.windowSeconds());
            if (actorWindows.size() >= config.maxActors()) {
                log.warn("audit_alert_maxActors reached, skipping burst tracking for actor={}", actorId);
                return;
            }
        }

        Instant now = clock.instant();
        Instant windowStart = now.minus(Duration.ofSeconds(config.windowSeconds()));

        ActorDeniedWindow window = actorWindows.compute(actorId, (k, existing) -> {
            if (existing == null) {
                return new ActorDeniedWindow();
            }
            // Clean old events outside window
            existing.events.removeIf(e -> e.timestamp().isBefore(windowStart));
            return existing;
        });

        // Record this event
        window.events.add(new DeniedEvent(now, action, targetTenantId, requestId, traceId));

        // Check threshold
        if (window.events.size() >= config.threshold()) {
            // Check cooldown
            Instant cooldownStart = now.minus(Duration.ofSeconds(config.cooldownSeconds()));
            if (window.lastAlertAt == null || window.lastAlertAt.isBefore(cooldownStart)) {
                window.lastAlertAt = now;
                fireBurstAlert(actorId, window.events, config);
            }
        }
    }

    private void fireBurstAlert(String actorId, Deque<DeniedEvent> events,
                                AuditAlertProperties.DeniedBurst config) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(Duration.ofSeconds(config.windowSeconds()));

        // Collect sample actions (max 5)
        List<String> sampleActions = new ArrayList<>();
        List<String> sampleTenantIds = new ArrayList<>();
        Instant firstSeen = null;
        Instant lastSeen = null;

        for (DeniedEvent event : events) {
            if (event.timestamp().isBefore(windowStart)) continue;
            if (firstSeen == null) firstSeen = event.timestamp();
            lastSeen = event.timestamp();

            if (sampleActions.size() < 5 && event.action() != null && !sampleActions.contains(event.action())) {
                sampleActions.add(event.action());
            }
            if (sampleTenantIds.size() < 5 && event.targetTenantId() != null && !sampleTenantIds.contains(event.targetTenantId())) {
                sampleTenantIds.add(event.targetTenantId());
            }
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("deniedCount", events.size());
        attrs.put("windowSeconds", config.windowSeconds());
        attrs.put("cooldownSeconds", config.cooldownSeconds());
        attrs.put("firstSeenAt", firstSeen != null ? firstSeen.toString() : "unknown");
        attrs.put("lastSeenAt", lastSeen != null ? lastSeen.toString() : "unknown");
        attrs.put("sampleActions", sampleActions);
        attrs.put("sampleTargetTenantIds", sampleTenantIds);

        publishAlert(SecurityAlert.withAttributes(
                "ADMIN_DENIED_BURST", "HIGH", "ADMIN_AUDIT", "ADMIN_DENIED_BURST",
                "ADMIN", actorId, "audit_record", null, null, "DENIED",
                "", "", attrs));
    }

    /**
     * Evict stale windows to free memory.
     */
    private void evictStaleWindows(long windowSeconds) {
        Instant cutoff = clock.instant().minus(Duration.ofSeconds(windowSeconds * 2));
        actorWindows.entrySet().removeIf(entry -> {
            ActorDeniedWindow window = entry.getValue();
            window.events.removeIf(e -> e.timestamp().isBefore(cutoff));
            return window.events.isEmpty() && (window.lastAlertAt == null || window.lastAlertAt.isBefore(cutoff));
        });
    }

    /**
     * Publish alert through SecurityAlertPort, catching any failures.
     */
    private void publishAlert(SecurityAlert alert) {
        try {
            alertPublisher.publish(alert);
        } catch (Exception e) {
            log.warn("SecurityAlertPort.publish failed: rule={} error={}", alert.rule(), e.getMessage());
        }
    }

    // ==================== Internal state ====================

    static class ActorDeniedWindow {
        Deque<DeniedEvent> events = new ConcurrentLinkedDeque<>();
        volatile Instant lastAlertAt;
    }

    record DeniedEvent(Instant timestamp, String action, String targetTenantId,
                       String requestId, String traceId) {}
}
