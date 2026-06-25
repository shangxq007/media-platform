package com.example.platform.outbox.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled outbox event dispatcher — polls outbox_events and publishes
 * them as Spring application events via registration-based routing.
 *
 * <p>Event type → Java class mapping is managed by {@link OutboxEventRouter}
 * and registered via {@link OutboxEventRegistration}. New event types no longer
 * require dispatcher code changes.</p>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final OutboxEventService service;
    private final ApplicationEventPublisher publisher;
    private final OutboxEventRouter router;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRetries;

    private final Counter eventsDispatchedCounter;
    private final Counter eventsFailedCounter;
    private final Counter eventsRetriedCounter;
    private final Timer dispatchTimer;

    private final String processorId = UUID.randomUUID().toString();

    public OutboxEventDispatcher(OutboxEventService service,
            ApplicationEventPublisher publisher,
            OutboxEventRouter router,
            @Value("${app.outbox.max-retries:3}") int maxRetries,
            MeterRegistry meterRegistry) {
        this.service = service;
        this.publisher = publisher;
        this.router = router;
        this.maxRetries = maxRetries;

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
            if (retried > 0) log.debug("Outbox retry cycle dispatched {} events", retried);
        } catch (Exception ex) {
            log.warn("Outbox retry skipped: {}", ex.getMessage());
        }
    }

    public boolean processOnce(String outboxId) {
        Timer.Sample sample = Timer.start();
        boolean locked = service.lockForProcessing(outboxId, processorId);
        if (!locked) return false;

        Map<String, Object> row = service.readEvent(outboxId);
        if (row == null) return false;

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

    public int processBatch(int limit) {
        int processed = 0;
        for (Map<String, Object> row : service.pendingForDispatch(limit)) {
            String outboxId = String.valueOf(row.get("id"));
            try {
                if (processOnce(outboxId)) processed++;
            } catch (Exception ex) {
                log.error("Unexpected error processing outbox event {}: {}", outboxId, ex.getMessage(), ex);
            }
        }
        return processed;
    }

    public int retryDueEvents() {
        int reset = service.resetDueFailedEvents();
        if (reset > 0) log.info("Reset {} due outbox events to PENDING", reset);
        return processBatch(100);
    }

    @Transactional
    public void deadLetter(String outboxId, String reason) {
        service.markDeadLetter(outboxId, reason);
        log.info("Manually dead-lettered outbox event {}: {}", outboxId, reason);
    }

    /**
     * Convert an outbox row to a Spring event via the OutboxEventRouter.
     * The "notification.event.published" marker is handled as a special case
     * (it's not a typed event — it's a string marker for notification delivery).
     */
    private Object toSpringEvent(Map<String, Object> row) {
        String eventType = String.valueOf(row.get("event_type"));
        String payload = String.valueOf(row.get("payload"));

        if ("notification.event.published".equals(eventType)) {
            return "notification.event.published:" + payload;
        }

        try {
            Class<?> eventClass = router.resolve(eventType);
            if (eventClass == null) {
                log.error("Unknown event type '{}' — not registered in OutboxEventRouter. "
                        + "Register it in OutboxEventRegistration.", eventType);
                return null;
            }
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);
            return objectMapper.convertValue(payloadMap, eventClass);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to parse outbox event payload for type " + eventType, ex);
        }
    }
}
