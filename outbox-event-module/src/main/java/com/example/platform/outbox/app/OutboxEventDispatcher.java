package com.example.platform.outbox.app;

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
 * and registered by domain-owned catalogs. New event types no longer
 * require dispatcher code changes.</p>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final OutboxEventService service;
    private final ApplicationEventPublisher publisher;
    private final OutboxEventRouter router;
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
            var event = router.decode((String) row.get("event_type"), ((Number) row.get("event_version")).intValue(),
                    (String) row.get("aggregate_type"), (String) row.get("aggregate_id"), (String) row.get("payload"));
            String previousTenant = com.example.platform.shared.web.TenantContext.get();
            try {
                if (previousTenant != null && !previousTenant.equals(event.tenantId()))
                    throw new OutboxEventRouter.InvalidEvent("INVALID_EVENT_SCOPE", "Dispatch tenant differs from envelope");
                com.example.platform.shared.web.TenantContext.set(event.tenantId());
                publisher.publishEvent(event.payload());
            } finally {
                if (previousTenant == null) com.example.platform.shared.web.TenantContext.clear();
                else com.example.platform.shared.web.TenantContext.set(previousTenant);
            }
            service.markProcessed(outboxId);
            eventsDispatchedCounter.increment();
            log.info("Successfully dispatched outbox event {}", outboxId);
            return true;
        } catch (OutboxEventRouter.InvalidEvent ex) {
            service.quarantine(outboxId, ex.code(), ex.getMessage());
            eventsFailedCounter.increment();
            return false;
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

}
