package com.example.platform.events;

import com.example.platform.audit.api.event.AuditOutboxEvents;
import com.example.platform.audit.api.event.UsageAnomalyDetectedEvent;
import com.example.platform.audit.app.UsageAnomalyDetectionService;
import com.example.platform.outbox.app.OutboxEventDispatcher;
import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "preview"})
class UsageAnomalyPublicationTest extends PostgresTestContainerSupport {
    @Autowired UsageAnomalyDetectionService detector;
    @Autowired OutboxEventService outbox;
    @Autowired OutboxEventRouter router;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;

    private String prepare() {
        String user = "ep23-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) assertTrue(detector.analyzeSubmission("ep23", user, "default", "test").clean());
        return user;
    }
    private int observations(String user) {
        return jdbc.queryForObject("select count(*) from outbox_events where event_type = 'audit.usage.anomaly.detected' and payload::jsonb->'payload'->>'userId' = ?", Integer.class, user);
    }
    private Map<String,Object> observation(String user) {
        return jdbc.queryForMap("select * from outbox_events where event_type = 'audit.usage.anomaly.detected' and payload::jsonb->'payload'->>'userId' = ?", user);
    }

    @Test void outerRollbackDoesNotPublishOrAdvanceTheDerivedRiskView() {
        String user = prepare();
        var before = detector.getRiskProfile("ep23", user);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertFalse(detector.analyzeSubmission("ep23", user, "default", "test").clean());
            assertEquals(1, observations(user));
            tx.setRollbackOnly();
        });
        assertEquals(0, observations(user));
        assertSame(before, detector.getRiskProfile("ep23", user));
        detector.analyzeSubmission("ep23", user, "default", "test");
        assertEquals(1, observations(user));
        assertEquals(1, detector.getRiskProfile("ep23", user).recentMitigationActions().size(), "rolled-back mitigation must not leak into a later committed view");
    }

    @Test void databaseAppendFailureLeavesTheDerivedViewUnchangedAndNextObservationCanCommit() {
        String user = prepare();
        var before = detector.getRiskProfile("ep23", user);
        jdbc.execute("alter table outbox_events add constraint ep23_reject_anomaly check (event_type <> 'audit.usage.anomaly.detected') not valid");
        try {
            assertThrows(RuntimeException.class, () -> detector.analyzeSubmission("ep23", user, "default", "test"));
            assertEquals(0, observations(user));
            assertSame(before, detector.getRiskProfile("ep23", user));
        } finally {
            jdbc.execute("alter table outbox_events drop constraint ep23_reject_anomaly");
        }
        detector.analyzeSubmission("ep23", user, "default", "test");
        assertEquals(1, observations(user));
        assertEquals(1, detector.getRiskProfile("ep23", user).recentMitigationActions().size());
    }

    @Test void rejectedTenantDoesNotAdvanceTheDerivedViewOrLeaveMitigationHistory() {
        String user = prepare();
        var before = detector.getRiskProfile("ep23", user);
        try {
            TenantContext.set("foreign");
            assertThrows(IllegalArgumentException.class,
                    () -> detector.analyzeSubmission("ep23", user, "default", "test"));
        } finally { TenantContext.clear(); }
        assertEquals(0, observations(user));
        assertSame(before, detector.getRiskProfile("ep23", user));
        detector.analyzeSubmission("ep23", user, "default", "test");
        assertEquals(1, observations(user));
        assertEquals(1, detector.getRiskProfile("ep23", user).recentMitigationActions().size());
    }

    @Test void committedObservationKeepsIdentityAcrossDeliveryFailureAndReplayWithoutAConsumer() {
        String user = prepare();
        var before = detector.getRiskProfile("ep23", user);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            detector.analyzeSubmission("ep23", user, "default", "test");
            assertSame(before, detector.getRiskProfile("ep23", user), "view must wait for commit");
        });
        var row = observation(user);
        String id = (String) row.get("id");
        var fact = assertInstanceOf(UsageAnomalyDetectedEvent.class, router.decode(
                (String) row.get("event_type"), ((Number) row.get("event_version")).intValue(),
                (String) row.get("aggregate_type"), (String) row.get("aggregate_id"), (String) row.get("payload")).payload());
        assertEquals("ep23", fact.tenantId()); assertEquals(user, fact.userId());
        assertEquals("render_burst", fact.ruleType()); assertEquals(0.55, fact.score());
        assertEquals(1, detector.getRiskProfile("ep23", user).recentMitigationActions().size());
        var append = AuditOutboxEvents.ANOMALY.append("ep23", fact, (String) row.get("idempotency_key"));
        AtomicBoolean fail = new AtomicBoolean(true);
        var dispatcher = new OutboxEventDispatcher(outbox, event -> {
            if (fail.getAndSet(false)) throw new IllegalStateException("injected transport failure");
            context.publishEvent(event);
        }, router, 3, new SimpleMeterRegistry());
        assertFalse(dispatcher.processOnce(id));
        assertEquals("FAILED", outbox.readEvent(id).get("status"));
        assertEquals(row.get("payload"), outbox.readEvent(id).get("payload"));
        assertEquals(id, outbox.append(append));
        assertTrue(dispatcher.processOnce(id));
        assertEquals("PROCESSED", outbox.readEvent(id).get("status"));
        assertEquals(id, outbox.append(append));
        assertFalse(dispatcher.processOnce(id));
        assertEquals(1, observations(user));
        assertEquals(1, detector.getRiskProfile("ep23", user).recentMitigationActions().size());
        try {
            TenantContext.set("foreign");
            assertThrows(IllegalArgumentException.class, () -> outbox.append(append));
        } finally { TenantContext.clear(); }
        assertEquals("PROCESSED", outbox.readEvent(id).get("status"));
        assertEquals(row.get("payload"), outbox.readEvent(id).get("payload"));
    }
}
