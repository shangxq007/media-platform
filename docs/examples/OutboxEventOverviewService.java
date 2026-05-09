package com.example.platform.outbox.app;

import static com.example.platform.jooq.generated.tables.OutboxEvents.OUTBOX_EVENTS;

import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

/**
 * EXAMPLE: jOOQ code generation usage (reference implementation).
 *
 * <p>This file is a <strong>documentation example</strong> — it is NOT compiled
 * by the main build. To use it:</p>
 *
 * <ol>
 *   <li>Run {@code ./scripts/generate-jooq.sh} to generate jOOQ classes</li>
 *   <li>Copy this file to {@code outbox-event-module/src/main/java/com/example/platform/outbox/app/}</li>
 *   <li>Add {@code build/generated-sources/jooq} to the module's source sets</li>
 *   <li>Compile and run tests</li>
 * </ol>
 *
 * <p>This service demonstrates migrating {@link OutboxEventService#overview()}
 * from raw DSL to generated jOOQ classes.</p>
 *
 * <p><strong>Before (raw DSL):</strong></p>
 * <pre>{@code
 * dsl.selectOne()
 *    .from(table("outbox_events"))
 *    .where(field("status").eq(STATUS_PENDING))
 * }</pre>
 *
 * <p><strong>After (generated):</strong></p>
 * <pre>{@code
 * dsl.selectOne()
 *    .from(OUTBOX_EVENTS)
 *    .where(OUTBOX_EVENTS.STATUS.eq(STATUS_PENDING))
 * }</pre>
 */
@Service
public class OutboxEventOverviewService {

    private final DSLContext dsl;

    public OutboxEventOverviewService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Returns a status summary of outbox events using generated jOOQ classes.
     */
    public Map<String, Object> overview() {
        Integer pending = dsl.fetchCount(
                dsl.selectOne()
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventService.STATUS_PENDING))
        );
        Integer processing = dsl.fetchCount(
                dsl.selectOne()
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventService.STATUS_PROCESSING))
        );
        Integer processed = dsl.fetchCount(
                dsl.selectOne()
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventService.STATUS_PROCESSED))
        );
        Integer failed = dsl.fetchCount(
                dsl.selectOne()
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventService.STATUS_FAILED))
        );
        Integer deadLetter = dsl.fetchCount(
                dsl.selectOne()
                        .from(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventService.STATUS_DEAD_LETTER))
        );
        return Map.of(
                "module", "outbox-event-module",
                "status", "active",
                "description", "Outbox event module — persistence, dispatch, and retry. (jOOQ generated)",
                "pending", pending,
                "processing", processing,
                "processed", processed,
                "failed", failed,
                "deadLetter", deadLetter
        );
    }
}
