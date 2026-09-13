package com.example.platform.outbox.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.platform.typedschema.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS;
import org.jooq.impl.DSL;


@Service
public class OutboxEventService {
    private final OutboxEventRouter router;
    private static final Logger log = LoggerFactory.getLogger(OutboxEventService.class);
    static final long BASE_BACKOFF_MS = 1000L;

    /** Status: event is waiting to be processed. */
    public static final String STATUS_PENDING = "PENDING";
    /** Status: event is currently being processed (locked). */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** Status: event was successfully dispatched. */
    public static final String STATUS_PROCESSED = "PROCESSED";
    /** Status: event failed, may be retried. */
    public static final String STATUS_FAILED = "FAILED";
    /** Status: event exceeded max retries, no more attempts. */
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    private final DSLContext dsl;
    private final int maxRetries;
    private final PostgresNotificationService notifyService;

    public OutboxEventService(DSLContext dsl,
            @Value("${app.outbox.max-retries:3}") int maxRetries,
            PostgresNotificationService notifyService, OutboxEventRouter router) {
        this.dsl = dsl;
        this.router = router;
        if (maxRetries < 1 || maxRetries > 30) throw new IllegalArgumentException("Outbox max retries must be between 1 and 30");
        this.maxRetries = maxRetries;
        this.notifyService = notifyService;
    }

    // -------------------------------------------------------------------------
    // Overview / Queries
    // -------------------------------------------------------------------------

    public Map<String, Object> overview() {
        Integer pending = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING))
        );
        Integer processing = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSING))
        );
        Integer processed = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_PROCESSED))
        );
        Integer failed = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED))
        );
        Integer deadLetter = dsl.fetchCount(
                dsl.selectOne().from(OUTBOX_EVENTS).where(OUTBOX_EVENTS.STATUS.eq(STATUS_DEAD_LETTER))
        );
        return Map.of(
                "module", "outbox-event-module",
                "status", "active",
                "description", "Outbox event module — persistence, dispatch, and retry.",
                "pending", pending,
                "processing", processing,
                "processed", processed,
                "failed", failed,
                "deadLetter", deadLetter
        );
    }

    public List<Map<String, Object>> recent(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                        OUTBOX_EVENTS.LOCKED_AT,
                        OUTBOX_EVENTS.LOCKED_BY,
                        OUTBOX_EVENTS.CREATED_AT,
                        OUTBOX_EVENTS.PUBLISHED_AT
                )
                .from(OUTBOX_EVENTS)
                .orderBy(OUTBOX_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> deadLetterEvents(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_DEAD_LETTER))
                .orderBy(OUTBOX_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> failedEvents(int limit) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.LAST_ERROR_CODE,
                        OUTBOX_EVENTS.LAST_ERROR_MESSAGE,
                        OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED))
                .orderBy(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Returns events eligible for dispatch: PENDING with no future backoff,
     * or FAILED with next_attempt_at <= now and retry_count < max_retries.
     */
    public List<Map<String, Object>> pendingForDispatch(int limit) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.PAYLOAD,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(
                        OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING)
                                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.isNull()
                                        .or(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now)))
                                .or(
                                        OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED)
                                                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now))
                                                .and(OUTBOX_EVENTS.RETRY_COUNT.lt(OUTBOX_EVENTS.MAX_RETRIES))
                                )
                )
                .orderBy(OUTBOX_EVENTS.CREATED_AT.asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Read a single event by ID (unlocked, for reads after locking).
     */
    public Map<String, Object> readEvent(String outboxId) {
        return dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.AGGREGATE_TYPE,
                        OUTBOX_EVENTS.AGGREGATE_ID,
                        OUTBOX_EVENTS.EVENT_TYPE,
                        OUTBOX_EVENTS.EVENT_VERSION,
                        OUTBOX_EVENTS.PAYLOAD,
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES,
                        OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.CREATED_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .fetchOneMap();
    }

    // -------------------------------------------------------------------------
    // Append with idempotency
    // -------------------------------------------------------------------------

    @Transactional
    public String append(com.example.platform.outbox.api.event.OutboxAppend<?> append) {
        return appendUsing(append,dsl,notifyService::notifyOutboxEvent);
    }

    /** Join an already-active owner jOOQ transaction; never start an independent transaction. */
    public String appendInTransaction(com.example.platform.outbox.api.event.OutboxAppend<?> append, DSLContext transaction) {
        java.util.Objects.requireNonNull(transaction,"owner transaction required");
        transaction.connection(connection->{if(connection.getAutoCommit())throw new IllegalStateException("active owner transaction required");});
        return appendUsing(append,transaction,()->notifyService.notifyOutboxEvent(transaction));
    }

    private String appendUsing(com.example.platform.outbox.api.event.OutboxAppend<?> append,DSLContext dsl,Runnable notify) {
        String currentTenant = com.example.platform.shared.web.TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(append.tenantId()))
            throw new IllegalArgumentException("Outbox tenant scope mismatch");
        String payload = router.encode(append);
        String key = append.idempotencyKey();
        if (key != null && key.isBlank()) key = null;
        // Canonical schema has a non-unique idempotency index. Serialize same-key appends
        // in the existing domain/Spring transaction; no schema-error retry or second write path.
        if (key != null) dsl.execute("select pg_advisory_xact_lock(hashtextextended(?, 0))", key);
        var existing = key == null ? null : dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.IDEMPOTENCY_KEY.eq(key)).forUpdate().fetchOne();
        if (existing == null) {
            String id = "obx_" + java.util.UUID.randomUUID().toString().replace("-", "");
            dsl.insertInto(OUTBOX_EVENTS)
                    .columns(OUTBOX_EVENTS.ID, OUTBOX_EVENTS.AGGREGATE_TYPE, OUTBOX_EVENTS.AGGREGATE_ID,
                            OUTBOX_EVENTS.EVENT_TYPE, OUTBOX_EVENTS.EVENT_VERSION, OUTBOX_EVENTS.PAYLOAD,
                            OUTBOX_EVENTS.STATUS, OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.MAX_RETRIES,
                            OUTBOX_EVENTS.IDEMPOTENCY_KEY, OUTBOX_EVENTS.CREATED_AT)
                    .values(id, append.type().aggregateType(), append.aggregateId(), append.type().name(), append.type().version(),
                            payload, STATUS_PENDING, 0, maxRetries, key, LocalDateTime.now()).execute();
            notify.run();
            return id;
        }
        var decoded = router.decode(existing.getEventType(), existing.getEventVersion(), existing.getAggregateType(), existing.getAggregateId(), existing.getPayload());
        if (!append.tenantId().equals(decoded.tenantId()) || !append.type().name().equals(existing.getEventType())
                || append.type().version() != existing.getEventVersion()
                || !append.type().aggregateType().equals(existing.getAggregateType()) || !append.aggregateId().equals(existing.getAggregateId()))
            throw new IllegalArgumentException("Idempotency key belongs to a different event scope");
        if (STATUS_PENDING.equals(existing.getStatus()) || STATUS_FAILED.equals(existing.getStatus())) {
            dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.PAYLOAD, payload).set(OUTBOX_EVENTS.STATUS, STATUS_PENDING)
                    .set(OUTBOX_EVENTS.RETRY_COUNT, 0).set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, (LocalDateTime) null)
                    .set(OUTBOX_EVENTS.LAST_ERROR_CODE, (String) null).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, (String) null)
                    .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null).set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                    .where(OUTBOX_EVENTS.ID.eq(existing.getId())).execute();
        }
        return existing.getId();
    }

    // -------------------------------------------------------------------------
    // Lock / unlock for processing
    // -------------------------------------------------------------------------

    /**
     * Lock a single outbox event for processing using SELECT FOR UPDATE.
     * Sets status to PROCESSING and records lock metadata.
     *
     * @return true if the event was locked successfully, false if it was not processable
     */
    @Transactional
    public boolean lockForProcessing(String outboxId, String processorId) {
        LocalDateTime now = LocalDateTime.now();

        // Lock the row with SELECT FOR UPDATE
        Map<String, Object> row = dsl.select(
                        OUTBOX_EVENTS.ID,
                        OUTBOX_EVENTS.STATUS,
                        OUTBOX_EVENTS.NEXT_ATTEMPT_AT
                )
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .forUpdate()
                .fetchOneMap();

        if (row == null) {
            return false;
        }

        String status = String.valueOf(row.get("status"));

        // Only process PENDING or FAILED (with expired backoff) events
        boolean isProcessable = STATUS_PENDING.equals(status) ||
                (STATUS_FAILED.equals(status) && row.get("next_attempt_at") != null
                        && !parseLocalDateTime(row.get("next_attempt_at")).isAfter(now));

        if (!isProcessable) {
            return false;
        }

        // Set to PROCESSING
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, STATUS_PROCESSING)
                .set(OUTBOX_EVENTS.LOCKED_AT, java.time.Instant.now())
                .set(OUTBOX_EVENTS.LOCKED_BY, processorId)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .execute();

        return true;
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    /**
     * Mark an event as successfully processed.
     */
    @Transactional
    public void markProcessed(String outboxId) {
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, STATUS_PROCESSED)
                .set(OUTBOX_EVENTS.PUBLISHED_AT, LocalDateTime.now())
                .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE, (String) null)
                .set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, (String) null)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .execute();
    }

    /**
     * Mark an event as failed with error details and exponential backoff.
     * If retry count exceeds max retries, moves to DEAD_LETTER.
     */
    @Transactional
    public void markFailedWithDetails(String outboxId, String errorCode, String errorMessage) {
        // Increment retry count and record error
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.RETRY_COUNT.plus(1))
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE, errorCode)
                .set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, errorMessage)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .execute();

        // Read updated retry count and max_retries
        Map<String, Object> row = dsl.select(
                        OUTBOX_EVENTS.RETRY_COUNT,
                        OUTBOX_EVENTS.MAX_RETRIES)
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .fetchOneMap();

        if (row == null) {
            return;
        }

        if (!(row.get("retry_count") instanceof Number retries) || retries.intValue() < 1) {
            quarantine(outboxId, "INVALID_RETRY_POLICY", "Missing or invalid persisted retry_count");
            return;
        }
        int retryCount = retries.intValue();
        if (!(row.get("max_retries") instanceof Number limit) || limit.intValue() < 1 || limit.intValue() > 30) {
            quarantine(outboxId, "INVALID_RETRY_POLICY", "Missing or invalid persisted max_retries");
            return;
        }
        int rowMaxRetries = limit.intValue();

        if (retryCount >= rowMaxRetries) {
            // Exceeded max retries → DEAD_LETTER
            dsl.update(OUTBOX_EVENTS)
                    .set(OUTBOX_EVENTS.STATUS, STATUS_DEAD_LETTER)
                    .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                    .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                    .where(OUTBOX_EVENTS.ID.eq(outboxId))
                    .execute();
        } else {
            // Exponential backoff: nextAttemptAt = now + (baseDelay * 2^retryCount)
            long backoffMs = BASE_BACKOFF_MS * (1L << retryCount);
            LocalDateTime nextAttempt = LocalDateTime.now().plusNanos(backoffMs * 1_000_000L);
            dsl.update(OUTBOX_EVENTS)
                    .set(OUTBOX_EVENTS.STATUS, STATUS_FAILED)
                    .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, nextAttempt)
                    .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                    .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                    .where(OUTBOX_EVENTS.ID.eq(outboxId))
                    .execute();
        }
    }

    /**
     * Reset expired FAILED events to PENDING so they can be retried.
     *
     * @return number of events reset
     */
    @Transactional
    public int resetDueFailedEvents() {
        LocalDateTime now = LocalDateTime.now();
        return dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, STATUS_PENDING)
                .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, (LocalDateTime) null)
                .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .where(OUTBOX_EVENTS.STATUS.eq(STATUS_FAILED))
                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(now))
                .execute();
    }

    /**
     * Manually move an event to DEAD_LETTER status.
     */
    @Transactional
    public void markDeadLetter(String outboxId, String reason) {
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, STATUS_DEAD_LETTER)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE, "MANUAL")
                .set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, reason)
                .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .and(OUTBOX_EVENTS.STATUS.ne(STATUS_PROCESSED))
                .execute();
    }

    @Transactional
    public void quarantine(String outboxId, String code, String reason) {
        dsl.update(OUTBOX_EVENTS).set(OUTBOX_EVENTS.STATUS, STATUS_DEAD_LETTER)
                .set(OUTBOX_EVENTS.LAST_ERROR_CODE, code).set(OUTBOX_EVENTS.LAST_ERROR_MESSAGE, reason)
                .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null).set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .where(OUTBOX_EVENTS.ID.eq(outboxId)).execute();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static LocalDateTime parseLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return LocalDateTime.parse(String.valueOf(value));
    }
}
