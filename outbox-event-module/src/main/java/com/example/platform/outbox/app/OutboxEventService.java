package com.example.platform.outbox.app;

import com.example.platform.shared.Ids;
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
            PostgresNotificationService notifyService) {
        this.dsl = dsl;
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
                        DSL.field(DSL.name("last_error_code")),
                        DSL.field(DSL.name("last_error_message")),
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
                        DSL.field(DSL.name("last_error_code")),
                        DSL.field(DSL.name("last_error_message")),
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
                        DSL.field(DSL.name("last_error_code")),
                        DSL.field(DSL.name("last_error_message")),
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
    public String appendEvent(String aggregateType, String aggregateId, String eventType,
            int eventVersion, Object payload) {
        return appendEvent(aggregateType, aggregateId, eventType, eventVersion, payload, null);
    }

    @Transactional
    public String appendEvent(String aggregateType, String aggregateId, String eventType,
            int eventVersion, Object payload, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<Map<String, Object>> existing = dsl.select(
                            OUTBOX_EVENTS.ID, OUTBOX_EVENTS.STATUS)
                    .from(OUTBOX_EVENTS)
                    .where(OUTBOX_EVENTS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                    .fetchMaps();
            if (!existing.isEmpty()) {
                String existingStatus = String.valueOf(existing.get(0).get("status"));
                String existingId = String.valueOf(existing.get(0).get("id"));
                // If already processed, return the existing id (no duplicate)
                if (STATUS_PROCESSED.equals(existingStatus)) {
                    return existingId;
                }
                // If PENDING or FAILED, update payload and reset to PENDING
                if (STATUS_PENDING.equals(existingStatus) || STATUS_FAILED.equals(existingStatus)) {
                    dsl.update(OUTBOX_EVENTS)
                            .set(OUTBOX_EVENTS.PAYLOAD, OutboxPayloadJson.toJson(payload))
                            .set(OUTBOX_EVENTS.STATUS, STATUS_PENDING)
                            .set(OUTBOX_EVENTS.RETRY_COUNT, 0)
                            .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, (LocalDateTime) null)
                            .set(DSL.field(DSL.name("last_error_code")), (String) null)
                            .set(DSL.field(DSL.name("last_error_message")), (String) null)
                            .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                            .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                            .where(OUTBOX_EVENTS.ID.eq(existingId))
                            .execute();
                    return existingId;
                }
                // For other statuses (PROCESSING, DEAD_LETTER), return existing id as-is
                return existingId;
            }
        }

        String id = Ids.newId("obx");
        try {
            dsl.insertInto(OUTBOX_EVENTS)
                    .columns(
                            OUTBOX_EVENTS.ID,
                            OUTBOX_EVENTS.AGGREGATE_TYPE,
                            OUTBOX_EVENTS.AGGREGATE_ID,
                            OUTBOX_EVENTS.EVENT_TYPE,
                            OUTBOX_EVENTS.EVENT_VERSION,
                            OUTBOX_EVENTS.PAYLOAD,
                            OUTBOX_EVENTS.STATUS,
                            OUTBOX_EVENTS.RETRY_COUNT,
                            OUTBOX_EVENTS.MAX_RETRIES,
                            OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                            OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                            OUTBOX_EVENTS.CREATED_AT,
                            OUTBOX_EVENTS.PUBLISHED_AT
                    )
                    .values(
                            id,
                            aggregateType,
                            aggregateId,
                            eventType,
                            eventVersion,
                            OutboxPayloadJson.toJson(payload),
                            STATUS_PENDING,
                            0,
                            maxRetries,
                            (LocalDateTime) null,
                            idempotencyKey,
                            LocalDateTime.now(),
                            null
                    )
                    .execute();
        } catch (Exception ex) {
            // Fallback: insert without max_retries column (for compatibility with older schema)
            log.warn("Outbox insert with max_retries failed, retrying without: {}", ex.getMessage());
            dsl.insertInto(OUTBOX_EVENTS)
                    .columns(
                            OUTBOX_EVENTS.ID,
                            OUTBOX_EVENTS.AGGREGATE_TYPE,
                            OUTBOX_EVENTS.AGGREGATE_ID,
                            OUTBOX_EVENTS.EVENT_TYPE,
                            OUTBOX_EVENTS.EVENT_VERSION,
                            OUTBOX_EVENTS.PAYLOAD,
                            OUTBOX_EVENTS.STATUS,
                            OUTBOX_EVENTS.RETRY_COUNT,
                            OUTBOX_EVENTS.NEXT_ATTEMPT_AT,
                            OUTBOX_EVENTS.IDEMPOTENCY_KEY,
                            OUTBOX_EVENTS.CREATED_AT,
                            OUTBOX_EVENTS.PUBLISHED_AT
                    )
                    .values(
                            id,
                            aggregateType,
                            aggregateId,
                            eventType,
                            eventVersion,
                            OutboxPayloadJson.toJson(payload),
                            STATUS_PENDING,
                            0,
                            (LocalDateTime) null,
                            idempotencyKey,
                            LocalDateTime.now(),
                            null
                    )
                    .execute();
        }
        notifyService.notifyOutboxEvent();
        return id;
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
                .set(DSL.field(DSL.name("last_error_code")), (String) null)
                .set(DSL.field(DSL.name("last_error_message")), (String) null)
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
                .set(DSL.field(DSL.name("last_error_code")), errorCode)
                .set(DSL.field(DSL.name("last_error_message")), errorMessage)
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

        int retryCount = ((Number) row.get("retry_count")).intValue();
        int rowMaxRetries = row.get("max_retries") == null ? maxRetries : ((Number) row.get("max_retries")).intValue();

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
                .set(DSL.field(DSL.name("last_error_code")), "MANUAL")
                .set(DSL.field(DSL.name("last_error_message")), reason)
                .set(OUTBOX_EVENTS.LOCKED_AT, (Instant) null)
                .set(OUTBOX_EVENTS.LOCKED_BY, (String) null)
                .where(OUTBOX_EVENTS.ID.eq(outboxId))
                .and(OUTBOX_EVENTS.STATUS.ne(STATUS_PROCESSED))
                .execute();
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
