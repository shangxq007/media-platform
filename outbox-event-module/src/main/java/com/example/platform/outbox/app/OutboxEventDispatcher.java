package com.example.platform.outbox.app;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final OutboxEventService service;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRetries;

    // Metrics
    private final Counter eventsDispatchedCounter;
    private final Counter eventsFailedCounter;
    private final Counter eventsRetriedCounter;
    private final Timer dispatchTimer;

    private final String processorId = UUID.randomUUID().toString();

    public OutboxEventDispatcher(OutboxEventService service,
            ApplicationEventPublisher publisher,
            @Value("${app.outbox.max-retries:3}") int maxRetries,
            MeterRegistry meterRegistry) {
        this.service = service;
        this.publisher = publisher;
        this.maxRetries = maxRetries;

        // Initialize metrics
        this.eventsDispatchedCounter = Counter.builder("outbox.events.dispatched")
                .description("Number of outbox events successfully dispatched")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("outbox.events.failed")
                .description("Number of outbox events that failed to dispatch")
                .register(meterRegistry);

        this.eventsRetriedCounter = Counter.builder("outbox.events.retried")
                .description("Number of outbox events retried")
                .register(meterRegistry);

        this.dispatchTimer = Timer.builder("outbox.dispatch.time")
                .description("Duration of outbox event dispatch operations")
                .register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // Scheduled dispatch
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${app.outbox.dispatch-interval-ms:3000}")
    public void scheduledDispatch() {
        try {
            processBatch(100);
        } catch (Exception ex) {
            log.warn("Outbox dispatch skipped: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms:30000}")
    public void scheduledRetry() {
        try {
            int retried = retryDueEvents();
            if (retried > 0) {
                log.debug("Outbox retry cycle dispatched {} events", retried);
            }
        } catch (Exception ex) {
            log.warn("Outbox retry skipped: {}", ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // processOnce — process a single event with SELECT FOR UPDATE
    // -------------------------------------------------------------------------

    /**
     * Process a single outbox event by ID.
     * Locks the row, sets PROCESSING, dispatches, then marks PROCESSED or FAILED.
     *
     * @return true if the event was dispatched successfully
     */
    public boolean processOnce(String outboxId) {
        Timer.Sample sample = Timer.start();

        boolean locked = service.lockForProcessing(outboxId, processorId);
        if (!locked) {
            log.debug("Event {} is not processable (already processed, locked, or not yet due)", outboxId);
            return false;
        }

        // Re-read the full row for dispatch (now locked)
        Map<String, Object> row = service.readEvent(outboxId);
        if (row == null) {
            return false;
        }

        try {
            Object event = toSpringEvent(row);
            if (event == null) {
                service.markFailedWithDetails(outboxId, "UNKNOWN_EVENT_TYPE",
                        "No handler for event type: " + row.get("event_type"));
                eventsFailedCounter.increment();
                log.warn("Failed outbox event {}: unknown event type {}", outboxId, row.get("event_type"));
                return false;
            }
            publisher.publishEvent(event);
            service.markProcessed(outboxId);
            eventsDispatchedCounter.increment();
            log.info("Successfully dispatched outbox event {}", outboxId);
            return true;
        } catch (Exception ex) {
            service.markFailedWithDetails(outboxId, "DISPATCH_ERROR", ex.getMessage());
            eventsFailedCounter.increment();
            eventsRetriedCounter.increment();
            log.warn("Failed to dispatch outbox event {}, will retry with backoff: {}",
                    outboxId, ex.getMessage());
            return false;
        } finally {
            sample.stop(dispatchTimer);
        }
    }

    // -------------------------------------------------------------------------
    // processBatch — batch processing
    // -------------------------------------------------------------------------

    /**
     * Fetch pending events and process each one individually.
     *
     * @return number of events successfully dispatched
     */
    public int processBatch(int limit) {
        int processed = 0;
        for (Map<String, Object> row : service.pendingForDispatch(limit)) {
            String outboxId = String.valueOf(row.get("id"));
            try {
                if (processOnce(outboxId)) {
                    processed++;
                }
            } catch (Exception ex) {
                log.error("Unexpected error processing outbox event {}: {}", outboxId, ex.getMessage(), ex);
            }
        }
        return processed;
    }

    // -------------------------------------------------------------------------
    // retryDueEvents — retry failed events whose backoff has expired
    // -------------------------------------------------------------------------

    /**
     * Reset expired FAILED events to PENDING and process them.
     *
     * @return number of events successfully retried
     */
    public int retryDueEvents() {
        int reset = service.resetDueFailedEvents();
        if (reset > 0) {
            log.info("Reset {} due outbox events to PENDING", reset);
        }
        // Process the reset events
        return processBatch(100);
    }

    // -------------------------------------------------------------------------
    // deadLetter — manually move an event to dead letter
    // -------------------------------------------------------------------------

    /**
     * Manually move an event to DEAD_LETTER status.
     */
    @Transactional
    public void deadLetter(String outboxId, String reason) {
        service.markDeadLetter(outboxId, reason);
        log.info("Manually dead-lettered outbox event {}: {}", outboxId, reason);
    }

    // -------------------------------------------------------------------------
    // Event routing
    // -------------------------------------------------------------------------

    /**
     * Convert an outbox row to a Spring application event based on event_type.
     * Handles all supported event types:
     * <ul>
     *   <li>render.job.created → RenderJobCreatedEvent</li>
     *   <li>render.job.status.changed → RenderJobStatusChangedEvent</li>
     *   <li>render.job.completed → RenderJobCompletedEvent</li>
     *   <li>render.job.failed → RenderJobFailedEvent</li>
     *   <li>artifact.created → ArtifactCreatedEvent</li>
     *   <li>notification.event.published → published as string marker</li>
     * </ul>
     */
    private Object toSpringEvent(Map<String, Object> row) {
        String eventType = String.valueOf(row.get("event_type"));
        String payload = String.valueOf(row.get("payload"));
        try {
            return switch (eventType) {
                case "render.job.created" -> objectMapper.convertValue(readMap(payload), RenderJobCreatedEvent.class);
                case "render.job.status.changed" -> objectMapper.convertValue(readMap(payload), RenderJobStatusChangedEvent.class);
                case "render.job.completed" -> objectMapper.convertValue(readMap(payload), RenderJobCompletedEvent.class);
                case "render.job.failed" -> objectMapper.convertValue(readMap(payload), RenderJobFailedEvent.class);
                case "artifact.created" -> objectMapper.convertValue(readMap(payload), ArtifactCreatedEvent.class);
                case "notification.event.published" -> "notification.event.published:" + payload;
                default -> null;
            };
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse outbox event payload for type " + eventType, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid outbox payload JSON", ex);
        }
    }
}
