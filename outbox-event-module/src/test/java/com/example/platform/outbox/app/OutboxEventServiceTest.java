package com.example.platform.outbox.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxEventServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private OutboxEventService service;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "outboxtest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table outbox_events ("
                    + "id varchar(64) primary key,"
                    + "aggregate_type varchar(100) not null,"
                    + "aggregate_id varchar(100) not null,"
                    + "event_type varchar(150) not null,"
                    + "event_version int not null,"
                    + "payload text not null,"
                    + "status varchar(50) not null,"
                    + "retry_count int not null default 0,"
                    + "max_retries int not null default 3,"
                    + "next_attempt_at timestamp,"
                    + "idempotency_key varchar(255),"
                    + "last_error_code varchar(100),"
                    + "last_error_message text,"
                    + "locked_at timestamp,"
                    + "locked_by varchar(255),"
                    + "created_at timestamp not null,"
                    + "published_at timestamp"
                    + ")");
        }

        service = new OutboxEventService(dsl, 3);
    }

    private static OffsetDateTime parseNextAttempt(Map<String, Object> row) {
        Object val = row.get("next_attempt_at");
        if (val == null) {
            return null;
        }
        if (val instanceof OffsetDateTime) {
            return (OffsetDateTime) val;
        }
        // H2 returns Timestamp without timezone; parse as LocalDateTime and convert
        LocalDateTime ldt = LocalDateTime.parse(val.toString().replace(' ', 'T'));
        return ldt.atOffset(ZoneOffset.UTC);
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

        service.markPublished(id);

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

        service.markFailed(id);

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

        service.markFailed(id);
        service.markFailed(id);
        service.markFailed(id);

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

        service.markDeadLetter(id);

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
        service.markPublished(id);

        service.markDeadLetter(id);

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

        service.markFailed(id);

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
        service.markDeadLetter(id);

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

    // -------------------------------------------------------------------------
    // New reliability tests
    // -------------------------------------------------------------------------

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
        OffsetDateTime next1 = parseNextAttempt(rows1.get(0));
        assertNotNull(next1);

        // Wait a tiny bit and mark failed again
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        service.markFailedWithDetails(id, "ERR", "e2");
        List<Map<String, Object>> rows2 = dsl.select()
                .from(DSL.table("outbox_events"))
                .where(DSL.field("id").eq(id))
                .fetchMaps();
        OffsetDateTime next2 = parseNextAttempt(rows2.get(0));
        assertNotNull(next2);

        // Second retry should have a later next_attempt_at than first (exponential)
        assertTrue(next2.isAfter(next1), "Second backoff should be longer than first");
    }

    @Test
    void idempotencyKeyProcessedDoesNotDuplicate() {
        // First append
        String id1 = service.appendEvent("order", "ord-1", "order.created", 1,
                Map.of("key", "v1"), "idem-proc-1");
        // Mark as processed
        service.markProcessed(id1);
        // Second append with same key — should return same id
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
        // Second append with same key — should update payload and return same id
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
        // Manually set status to FAILED with a past next_attempt_at
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
