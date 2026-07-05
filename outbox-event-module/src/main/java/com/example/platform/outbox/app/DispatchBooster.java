package com.example.platform.outbox.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fast poll booster — reduces average dispatch latency from 1.5s to 250ms.
 *
 * <p>Complements the 3s scheduled polling in OutboxEventDispatcher.
 * NOTIFY is sent by PostgresNotificationService after each write.
 * Full pgjdbc LISTEN integration is deferred to Phase 4.</p>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class DispatchBooster {

    private static final Logger log = LoggerFactory.getLogger(DispatchBooster.class);
    private final OutboxEventDispatcher outboxDispatcher;
    private final PlatformTaskDispatcher taskDispatcher;

    public DispatchBooster(OutboxEventDispatcher outboxDispatcher,
                              PlatformTaskDispatcher taskDispatcher) {
        this.outboxDispatcher = outboxDispatcher;
        this.taskDispatcher = taskDispatcher;
    }

    @Scheduled(fixedDelay = 500)
    public void boostOutbox() {
        try {
            outboxDispatcher.processBatch(100);
        } catch (Exception e) {
            log.trace("Boost outbox: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 500)
    public void boostTasks() {
        try {
            taskDispatcher.dispatch();
        } catch (Exception e) {
            log.trace("Boost tasks: {}", e.getMessage());
        }
    }
}
