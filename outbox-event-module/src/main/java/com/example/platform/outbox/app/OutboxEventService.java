package com.example.platform.outbox.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public OutboxEventService(DSLContext dsl,
            @Value("${app.outbox.max-retries:3}") int maxRetries) {
        this.dsl = dsl;
        this.maxRetries = maxRetries;
    }

    // -------------------------------------------------------------------------
    // Overview / Queries
    // -------------------------------------------------------------------------

    public Map<String, Object> overview() {
        Integer pending = dsl.fetchCount(
                dsl.selectOne().from(table("outbox_events")).where(field("status").eq(STATUS_PENDING))
        );
        Integer processing = dsl.fetchCount(
                dsl.selectOne().from(table("outbox_events")).where(field("status").eq(STATUS_PROCESSING))
        );
        Integer processed = dsl.fetchCount(
                dsl.selectOne().from(table("outbox_events")).where(field("status").eq(STATUS_PROCESSED))
        );
        Integer failed = dsl.fetchCount(
                dsl.selectOne().from(table("outbox_events")).where(field("status").eq(STATUS_FAILED))
        );
        Integer deadLetter = dsl.fetchCount(
                dsl.selectOne().from(table("outbox_events")).where(field("status").eq(STATUS_DEAD_LETTER))
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
                        field("id"),
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("event_version"),
                        field("status"),
                        field("retry_count"),
                        field("max_retries"),
                        field("last_error_code"),
                        field("last_error_message"),
                        field("next_attempt_at"),
                        field("locked_at"),
                        field("locked_by"),
                        field("created_at"),
                        field("published_at")
                )
                .from(table("outbox_events"))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> deadLetterEvents(int limit) {
        return dsl.select(
                        field("id"),
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("status"),
                        field("retry_count"),
                        field("last_error_code"),
                        field("last_error_message"),
                        field("created_at")
                )
                .from(table("outbox_events"))
                .where(field("status").eq(STATUS_DEAD_LETTER))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetchMaps();
    }

    public List<Map<String, Object>> failedEvents(int limit) {
        return dsl.select(
                        field("id"),
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("event_version"),
                        field("status"),
                        field("retry_count"),
                        field("max_retries"),
                        field("last_error_code"),
                        field("last_error_message"),
                        field("next_attempt_at"),
                        field("created_at")
                )
                .from(table("outbox_events"))
                .where(field("status").eq(STATUS_FAILED))
                .orderBy(field("next_attempt_at").asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Returns events eligible for dispatch: PENDING with no future backoff,
     * or FAILED with next_attempt_at <= now and retry_count < max_retries.
     */
    public List<Map<String, Object>> pendingForDispatch(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.select(
                        field("id"),
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("event_version"),
                        field("payload"),
                        field("retry_count"),
                        field("max_retries"),
                        field("idempotency_key"),
                        field("created_at")
                )
                .from(table("outbox_events"))
                .where(
                        field("status").eq(STATUS_PENDING)
                                .and(field("next_attempt_at").isNull()
                                        .or(field("next_attempt_at").le(now)))
                                .or(
                                        field("status").eq(STATUS_FAILED)
                                                .and(field("next_attempt_at").le(now))
                                                .and(field("retry_count").lt(field("max_retries", Integer.class)))
                                )
                )
                .orderBy(field("created_at").asc())
                .limit(limit)
                .fetchMaps();
    }

    /**
     * Read a single event by ID (unlocked, for reads after locking).
     */
    public Map<String, Object> readEvent(String outboxId) {
        return dsl.select(
                        field("id"),
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("event_version"),
                        field("payload"),
                        field("retry_count"),
                        field("max_retries"),
                        field("idempotency_key"),
                        field("status"),
                        field("created_at")
                )
                .from(table("outbox_events"))
                .where(field("id").eq(outboxId))
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
                            field("id"), field("status"))
                    .from(table("outbox_events"))
                    .where(field("idempotency_key").eq(idempotencyKey))
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
                    dsl.update(table("outbox_events"))
                            .set(field("payload"), Jsons.toJson(payload))
                            .set(field("status"), STATUS_PENDING)
                            .set(field("retry_count"), 0)
                            .set(field("next_attempt_at"), (OffsetDateTime) null)
                            .set(field("last_error_code"), (String) null)
                            .set(field("last_error_message"), (String) null)
                            .set(field("locked_at"), (OffsetDateTime) null)
                            .set(field("locked_by"), (String) null)
                            .where(field("id").eq(existingId))
                            .execute();
                    return existingId;
                }
                // For other statuses (PROCESSING, DEAD_LETTER), return existing id as-is
                return existingId;
            }
        }

        String id = Ids.newId("obx");
        try {
            dsl.insertInto(table("outbox_events"))
                    .columns(
                            field("id"),
                            field("aggregate_type"),
                            field("aggregate_id"),
                            field("event_type"),
                            field("event_version"),
                            field("payload"),
                            field("status"),
                            field("retry_count"),
                            field("max_retries"),
                            field("next_attempt_at"),
                            field("idempotency_key"),
                            field("created_at"),
                            field("published_at")
                    )
                    .values(
                            id,
                            aggregateType,
                            aggregateId,
                            eventType,
                            eventVersion,
                            Jsons.toJson(payload),
                            STATUS_PENDING,
                            0,
                            maxRetries,
                            (OffsetDateTime) null,
                            idempotencyKey,
                            OffsetDateTime.now(),
                            null
                    )
                    .execute();
        } catch (Exception ex) {
            // Fallback: insert without max_retries column (for compatibility with older schema)
            log.warn("Outbox insert with max_retries failed, retrying without: {}", ex.getMessage());
            dsl.insertInto(table("outbox_events"))
                    .columns(
                            field("id"),
                            field("aggregate_type"),
                            field("aggregate_id"),
                            field("event_type"),
                            field("event_version"),
                            field("payload"),
                            field("status"),
                            field("retry_count"),
                            field("next_attempt_at"),
                            field("idempotency_key"),
                            field("created_at"),
                            field("published_at")
                    )
                    .values(
                            id,
                            aggregateType,
                            aggregateId,
                            eventType,
                            eventVersion,
                            Jsons.toJson(payload),
                            STATUS_PENDING,
                            0,
                            (OffsetDateTime) null,
                            idempotencyKey,
                            OffsetDateTime.now(),
                            null
                    )
                    .execute();
        }
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
        OffsetDateTime now = OffsetDateTime.now();

        // Lock the row with SELECT FOR UPDATE
        Map<String, Object> row = dsl.select(
                        field("id"),
                        field("status"),
                        field("next_attempt_at")
                )
                .from(table("outbox_events"))
                .where(field("id").eq(outboxId))
                .forUpdate()
                .fetchOneMap();

        if (row == null) {
            return false;
        }

        String status = String.valueOf(row.get("status"));

        // Only process PENDING or FAILED (with expired backoff) events
        boolean isProcessable = STATUS_PENDING.equals(status) ||
                (STATUS_FAILED.equals(status) && row.get("next_attempt_at") != null
                        && !parseOffsetDateTime(row.get("next_attempt_at")).isAfter(now));

        if (!isProcessable) {
            return false;
        }

        // Set to PROCESSING
        dsl.update(table("outbox_events"))
                .set(field("status"), STATUS_PROCESSING)
                .set(field("locked_at"), now)
                .set(field("locked_by"), processorId)
                .where(field("id").eq(outboxId))
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
        dsl.update(table("outbox_events"))
                .set(field("status"), STATUS_PROCESSED)
                .set(field("published_at"), OffsetDateTime.now())
                .set(field("locked_at"), (OffsetDateTime) null)
                .set(field("locked_by"), (String) null)
                .set(field("last_error_code"), (String) null)
                .set(field("last_error_message"), (String) null)
                .where(field("id").eq(outboxId))
                .execute();
    }

    /**
     * Mark an event as failed with error details and exponential backoff.
     * If retry count exceeds max retries, moves to DEAD_LETTER.
     */
    @Transactional
    public void markFailedWithDetails(String outboxId, String errorCode, String errorMessage) {
        // Increment retry count and record error
        dsl.update(table("outbox_events"))
                .set(field("retry_count"), field("retry_count", Integer.class).plus(1))
                .set(field("last_error_code"), errorCode)
                .set(field("last_error_message"), errorMessage)
                .where(field("id").eq(outboxId))
                .execute();

        // Read updated retry count and max_retries
        Map<String, Object> row = dsl.select(
                        field("retry_count", Integer.class),
                        field("max_retries", Integer.class))
                .from(table("outbox_events"))
                .where(field("id").eq(outboxId))
                .fetchOneMap();

        if (row == null) {
            return;
        }

        int retryCount = ((Number) row.get("retry_count")).intValue();
        int rowMaxRetries = row.get("max_retries") == null ? maxRetries : ((Number) row.get("max_retries")).intValue();

        if (retryCount >= rowMaxRetries) {
            // Exceeded max retries → DEAD_LETTER
            dsl.update(table("outbox_events"))
                    .set(field("status"), STATUS_DEAD_LETTER)
                    .set(field("locked_at"), (OffsetDateTime) null)
                    .set(field("locked_by"), (String) null)
                    .where(field("id").eq(outboxId))
                    .execute();
        } else {
            // Exponential backoff: nextAttemptAt = now + (baseDelay * 2^retryCount)
            long backoffMs = BASE_BACKOFF_MS * (1L << retryCount);
            OffsetDateTime nextAttempt = OffsetDateTime.now().plusNanos(backoffMs * 1_000_000L);
            dsl.update(table("outbox_events"))
                    .set(field("status"), STATUS_FAILED)
                    .set(field("next_attempt_at"), nextAttempt)
                    .set(field("locked_at"), (OffsetDateTime) null)
                    .set(field("locked_by"), (String) null)
                    .where(field("id").eq(outboxId))
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
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.update(table("outbox_events"))
                .set(field("status"), STATUS_PENDING)
                .set(field("next_attempt_at"), (OffsetDateTime) null)
                .set(field("locked_at"), (OffsetDateTime) null)
                .set(field("locked_by"), (String) null)
                .where(field("status").eq(STATUS_FAILED))
                .and(field("next_attempt_at").le(now))
                .execute();
    }

    /**
     * Manually move an event to DEAD_LETTER status.
     */
    @Transactional
    public void markDeadLetter(String outboxId, String reason) {
        dsl.update(table("outbox_events"))
                .set(field("status"), STATUS_DEAD_LETTER)
                .set(field("last_error_code"), "MANUAL")
                .set(field("last_error_message"), reason)
                .set(field("locked_at"), (OffsetDateTime) null)
                .set(field("locked_by"), (String) null)
                .where(field("id").eq(outboxId))
                .and(field("status").ne(STATUS_PROCESSED))
                .execute();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static OffsetDateTime parseOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }
}
