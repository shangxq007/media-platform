package com.example.platform.lifecycle;

import com.example.platform.outbox.app.OutboxEventDispatcher;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Drains optional background resources during JVM shutdown (complements {@code server.shutdown=graceful}).
 */
@Component
public class PlatformGracefulShutdownCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PlatformGracefulShutdownCoordinator.class);

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private final Optional<OutboxEventDispatcher> outboxDispatcher;

    @Value("${platform.lifecycle.shutdown.outbox-drain-batch:50}")
    private int outboxDrainBatch;

    @Value("${platform.lifecycle.shutdown.outbox-drain-enabled:true}")
    private boolean outboxDrainEnabled;

    public PlatformGracefulShutdownCoordinator(
            @Autowired(required = false) OutboxEventDispatcher outboxDispatcher) {
        this.outboxDispatcher = Optional.ofNullable(outboxDispatcher);
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent event) {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        log.info("Platform graceful shutdown: draining background resources");
        drainOutbox();
        log.info("Platform graceful shutdown: background drain complete");
    }

    private void drainOutbox() {
        if (!outboxDrainEnabled || outboxDispatcher.isEmpty()) {
            return;
        }
        try {
            int processed = outboxDispatcher.get().processBatch(Math.max(1, outboxDrainBatch));
            log.info("Outbox shutdown drain processed {} events", processed);
        } catch (Exception e) {
            log.warn("Outbox shutdown drain skipped: {}", e.getMessage());
        }
    }

}
