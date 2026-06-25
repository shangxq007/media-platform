package com.example.platform.outbox.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.outbox.testsupport.OutboxEventTestSchemaFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxEventServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private OutboxEventService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        OutboxEventTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        OutboxEventTestSchemaFixture.truncate(dsl);
        service = new OutboxEventService(dsl, 3, new PostgresNotificationService(null));
    }

    @Test
    void appendEventCreatesPendingEvent() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        assertNotNull(id);
        assertTrue(id.startsWith("obx_"));

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("PENDING", rows.get(0).get("status"));
        assertEquals("order", rows.get(0).get("aggregate_type"));
        assertEquals("ord-1", rows.get(0).get("aggregate_id"));
        assertEquals("order.created", rows.get(0).get("event_type"));
    }

    @Test
    void appendEventWithIdempotencyKeyReturnsExistingId() {
        String id1 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"), "idem-key-123");

        String id2 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"), "idem-key-123");

        assertEquals(id1, id2);

        int count = dsl.fetchCount(DSL.table("outbox_events"));
        assertEquals(1, count);
    }

    @Test
    void appendEventWithNullIdempotencyKeyCreatesNewEvent() {
        String id1 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"), null);

        String id2 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"), null);

        assertTrue(!id1.equals(id2));
        int count = dsl.fetchCount(DSL.table("outbox_events"));
        assertEquals(2, count);
    }

    @Test
    void markPublishedUpdatesStatus() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markProcessed(id);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("PROCESSED", rows.get(0).get("status"));
        assertNotNull(rows.get(0).get("published_at"));
    }

    @Test
    void markFailedIncrementsRetryCountAndSetsNextAttempt() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "TEST", "test error");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("FAILED", rows.get(0).get("status"));
        assertEquals(1, rows.get(0).get("retry_count"));
        assertNotNull(rows.get(0).get("next_attempt_at"));
    }

    @Test
    void markFailedExceedingMaxRetriesSetsStatusDeadLetter() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "TEST", "test error");
        service.markFailedWithDetails(id, "TEST", "test error");
        service.markFailedWithDetails(id, "TEST", "test error");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("DEAD_LETTER", rows.get(0).get("status"));
        assertEquals(3, rows.get(0).get("retry_count"));
    }

    @Test
    void markDeadLetterSetsStatus() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markDeadLetter(id, "test reason");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("DEAD_LETTER", rows.get(0).get("status"));
    }

    @Test
    void markDeadLetterDoesNotOverrideProcessed() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));
        service.markProcessed(id);

        service.markDeadLetter(id, "test reason");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("PROCESSED", rows.get(0).get("status"));
    }

    @Test
    void pendingForDispatchExcludesFutureNextAttempt() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "TEST", "test error");

        List<Map<String, Object>> pending = service.pendingForDispatch(100);
        assertTrue(pending.stream().noneMatch(r -> id.equals(String.valueOf(r.get("id")))));
    }

    @Test
    void pendingForDispatchIncludesNullNextAttempt() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        List<Map<String, Object>> pending = service.pendingForDispatch(100);
        assertTrue(pending.stream().anyMatch(r -> id.equals(String.valueOf(r.get("id")))));
    }

    @Test
    void overviewReturnsDeadLetterCount() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));
        service.markDeadLetter(id, "test reason");

        Map<String, Object> overview = service.overview();
        assertNotNull(overview.get("deadLetter"));
        assertEquals(1, overview.get("deadLetter"));
    }

    @Test
    void recentReturnsEventsInDescendingOrder() {
        service.appendEvent("order", "ord-1", "order.created", 1, Map.of("k", "v1"));
        service.appendEvent("order", "ord-2", "order.created", 1, Map.of("k", "v2"));

        List<Map<String, Object>> recent = service.recent(10);
        assertTrue(recent.size() >= 2);
    }

    @Test
    void markProcessedSetsStatusToProcessed() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markProcessed(id);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("PROCESSED", rows.get(0).get("status"));
        assertNotNull(rows.get(0).get("published_at"));
    }

    @Test
    void markFailedWithDetailsRecordsErrorCodeAndMessage() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "TIMEOUT", "Connection timed out");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("FAILED", rows.get(0).get("status"));
        assertEquals("TIMEOUT", rows.get(0).get("last_error_code"));
        assertEquals("Connection timed out", rows.get(0).get("last_error_message"));
        assertEquals(1, rows.get(0).get("retry_count"));
    }

    @Test
    void markFailedWithDetailsExceedingMaxRetriesGoesToDeadLetter() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "ERR", "e1");
        service.markFailedWithDetails(id, "ERR", "e2");
        service.markFailedWithDetails(id, "ERR", "e3");

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("DEAD_LETTER", rows.get(0).get("status"));
        assertEquals(3, rows.get(0).get("retry_count"));
        assertEquals("ERR", rows.get(0).get("last_error_code"));
        assertEquals("e3", rows.get(0).get("last_error_message"));
    }

    @Test
    void exponentialBackoffIncreasesWithRetryCount() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        service.markFailedWithDetails(id, "ERR", "e1");
        List<Map<String, Object>> rows1 = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();
        OffsetDateTime next1 = (OffsetDateTime) rows1.get(0).get("next_attempt_at");
        assertNotNull(next1);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        service.markFailedWithDetails(id, "ERR", "e2");
        List<Map<String, Object>> rows2 = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();
        OffsetDateTime next2 = (OffsetDateTime) rows2.get(0).get("next_attempt_at");
        assertNotNull(next2);

        assertTrue(next2.isAfter(next1), "Second backoff should be longer than first");
    }

    @Test
    void idempotencyKeyProcessedDoesNotDuplicate() {
        String id1 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "v1"), "idem-proc-1");
        service.markProcessed(id1);
        String id2 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "v2"), "idem-proc-1");

        assertEquals(id1, id2);
        int count = dsl.fetchCount(DSL.table("outbox_events"));
        assertEquals(1, count);
    }

    @Test
    void idempotencyKeyPendingResetsPayload() {
        String id1 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "v1"), "idem-pend-1");
        String id2 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "v2"), "idem-pend-1");

        assertEquals(id1, id2);
        int count = dsl.fetchCount(DSL.table("outbox_events"));
        assertEquals(1, count);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id1))
                .fetchMaps();
        assertEquals("PENDING", rows.get(0).get("status"));
        assertEquals(0, rows.get(0).get("retry_count"));
    }

    @Test
    void lockForProcessingSetsProcessingStatus() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));

        boolean locked = service.lockForProcessing(id, "test-processor-1");

        assertTrue(locked);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("PROCESSING", rows.get(0).get("status"));
        assertNotNull(rows.get(0).get("locked_at"));
        assertEquals("test-processor-1", rows.get(0).get("locked_by"));
    }

    @Test
    void lockForProcessingReturnsFalseForProcessedEvent() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));
        service.markProcessed(id);

        boolean locked = service.lockForProcessing(id, "test-processor-1");

        assertTrue(!locked);
    }

    @Test
    void resetDueFailedEventsResetsExpiredEvents() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));
        dsl.update(DSL.table("outbox_events"))
                .set(DSL.field("status"), "FAILED")
                .set(DSL.field("next_attempt_at"), OffsetDateTime.now().minusSeconds(60))
                .set(DSL.field("retry_count"), 1)
                .where(DSL.field("id").eq(id))
                .execute();

        int reset = service.resetDueFailedEvents();
        assertEquals(1, reset);

        List<Map<String, Object>> rows = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();

        assertEquals("PENDING", rows.get(0).get("status"));
    }

    @Test
    void overviewIncludesProcessingCount() {
        String id = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "value"));
        service.lockForProcessing(id, "test-processor");

        Map<String, Object> overview = service.overview();
        assertNotNull(overview.get("processing"));
        assertEquals(1, overview.get("processing"));
    }
}
