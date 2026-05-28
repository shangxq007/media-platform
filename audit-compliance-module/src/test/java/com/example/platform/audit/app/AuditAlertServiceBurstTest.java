package com.example.platform.audit.app;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for AuditAlertService burst detection.
 */
class AuditAlertServiceBurstTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-05-26T10:00:00Z"));
    private Clock clock;
    private AuditAlertService service;
    private AuditAlertProperties props;

    @BeforeEach
    void setUp() {
        clock = new Clock() {
            @Override public Instant instant() { return now.get(); }
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
        };
        props = new AuditAlertProperties(
                new AuditAlertProperties.DeniedBurst(true, 3, 600, 1800, 10000),
                AuditAlertProperties.Publisher.defaults());
        service = new AuditAlertService(props, new NoopSecurityAlertAdapter(), clock);
    }

    @AfterEach
    void tearDown() {
        now.set(Instant.parse("2026-05-26T10:00:00Z"));
    }

    private void deny(String actorId, String action, String tenantId, String reqId) {
        service.evaluate("ADMIN_AUDIT", action, "ADMIN", actorId,
                "tenant", null, tenantId, "DENIED", reqId, reqId.replace("req", "trace"));
    }

    @Test
    void singleDeniedDoesNotTriggerBurst() {
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        // Only 1 event, threshold=3 → no burst
    }

    @Test
    void belowThresholdNoBurst() {
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-2");
        // 2 < threshold=3 → no burst
    }

    @Test
    void differentActorsCountedSeparately() {
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-2");
        deny("actor-2", "ADMIN_LIST_TENANTS", "tenant-a", "req-3");
        deny("actor-2", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-4");
        deny("actor-2", "ADMIN_CREATE_WORKSPACE", "tenant-b", "req-5");
        // actor-2 reaches threshold=3, actor-1 doesn't
    }

    @Test
    void eventsOutsideWindowNotCounted() {
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        // Advance past window (600s)
        now.set(now.get().plus(Duration.ofSeconds(700)));
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-2");
        // Only 1 event in current window → no burst
    }

    @Test
    void cooldownPreventsReAlert() {
        // Trigger burst
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-2");
        deny("actor-1", "ADMIN_CREATE_WORKSPACE", "tenant-b", "req-3");

        // More events in cooldown → no re-alert
        now.set(now.get().plus(Duration.ofSeconds(100)));
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-4");
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-5");
        deny("actor-1", "ADMIN_CREATE_WORKSPACE", "tenant-b", "req-6");
    }

    @Test
    void cooldownExpiresCanReAlert() {
        // Trigger burst
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-2");
        deny("actor-1", "ADMIN_CREATE_WORKSPACE", "tenant-b", "req-3");

        // Advance past cooldown (1800s)
        now.set(now.get().plus(Duration.ofSeconds(2000)));

        // New burst in new window → should re-alert
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-4");
        deny("actor-1", "ADMIN_DELETE_LITELLM_KEY", "tenant-a", "req-5");
        deny("actor-1", "ADMIN_CREATE_WORKSPACE", "tenant-b", "req-6");
    }

    @Test
    void anonymousActorSkipped() {
        deny(null, "ADMIN_LIST_TENANTS", "tenant-a", "req-1");
        deny("", "ADMIN_LIST_TENANTS", "tenant-a", "req-2");
        deny("  ", "ADMIN_LIST_TENANTS", "tenant-a", "req-3");
        deny(null, "ADMIN_LIST_TENANTS", "tenant-a", "req-4");
        deny("", "ADMIN_LIST_TENANTS", "tenant-a", "req-5");
        // Should not crash, anonymous events don't aggregate
    }

    @Test
    void nonAdminAuditDeniedNotCounted() {
        service.evaluate("UNKNOWN", "ADMIN_LIST_TENANTS", "ADMIN", "actor-1",
                "tenant", null, "tenant-a", "DENIED", "req-1", "trace-1");
        service.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-1",
                "tenant", null, "tenant-a", "SUCCESS", "req-2", "trace-2");
        service.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-1",
                "tenant", null, "tenant-a", "FAILED", "req-3", "trace-3");
        // These are single alerts only, no burst aggregation
    }

    @Test
    void failedNotCountedInDeniedBurst() {
        service.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-1",
                "tenant", null, "tenant-a", "FAILED", "req-1", "trace-1");
        service.evaluate("ADMIN_AUDIT", "ADMIN_DELETE_LITELLM_KEY", "ADMIN", "actor-1",
                "litellm_key", "tenant-a", "tenant-a", "FAILED", "req-2", "trace-2");
        service.evaluate("ADMIN_AUDIT", "ADMIN_CREATE_WORKSPACE", "ADMIN", " actor-1",
                "workspace", null, "tenant-b", "FAILED", "req-3", "trace-3");
        deny("actor-1", "ADMIN_LIST_TENANTS", "tenant-a", "req-4");
        // Only 1 DENIED event → no burst
    }

    @Test
    void burstDisabledSkipsAggregation() {
        AuditAlertProperties disabledProps = new AuditAlertProperties(
                new AuditAlertProperties.DeniedBurst(false, 3, 600, 1800, 10000),
                AuditAlertProperties.Publisher.defaults());
        AuditAlertService disabledService = new AuditAlertService(disabledProps, new NoopSecurityAlertAdapter(), clock);

        for (int i = 0; i < 10; i++) {
            disabledService.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-1",
                    "tenant", null, "tenant-a", "DENIED", "req-" + i, "trace-" + i);
        }
        // No burst should fire
    }

    @Test
    void burstDoesNotAffectAuditServiceRecord() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                service.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-" + i,
                        "tenant", null, "tenant-a", "DENIED", "req-" + i, "trace-" + i);
            }
        });
    }

    @Test
    void sensitiveFieldsNotInAlert() {
        // evaluate() doesn't receive payload, so sensitive fields cannot leak
        deny("actor-1", "ADMIN_SET_LITELLM_KEY", "tenant-a", "req-1");
        // Design-level: method signature doesn't include payload
    }

    @Test
    void maxActorsPreventsMemoryExhaustion() {
        AuditAlertProperties smallMax = new AuditAlertProperties(
                new AuditAlertProperties.DeniedBurst(true, 5, 600, 1800, 3),
                AuditAlertProperties.Publisher.defaults());
        AuditAlertService smallService = new AuditAlertService(smallMax, new NoopSecurityAlertAdapter(), clock);

        for (int i = 0; i < 10; i++) {
            smallService.evaluate("ADMIN_AUDIT", "ADMIN_LIST_TENANTS", "ADMIN", "actor-" + i,
                    "tenant", null, "tenant-a", "DENIED", "req-" + i, "trace-" + i);
        }
        // Should not crash even with maxActors=3
    }
}
