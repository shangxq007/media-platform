package com.example.platform.workflow.temporal;

import io.temporal.worker.WorkerFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stops Temporal poll loops and waits for in-flight activities before JVM exit.
 */
@ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component

public class TemporalWorkerGracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerGracefulShutdown.class);

    private final java.util.concurrent.atomic.AtomicBoolean shutdownStarted = new java.util.concurrent.atomic.AtomicBoolean();

    private final WorkerFactory workerFactory;
    private final AppTemporalProperties temporalProperties;

    public TemporalWorkerGracefulShutdown(WorkerFactory workerFactory, AppTemporalProperties temporalProperties) {
        this.workerFactory = workerFactory;
        this.temporalProperties = temporalProperties;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextClosed(ContextClosedEvent event) {
        if (!shutdownStarted.compareAndSet(false, true)) return;
        log.info("Temporal worker graceful shutdown: initiating WorkerFactory.shutdown()");
        Exception failure = null;
        try { workerFactory.shutdown(); }
        catch (Exception e) { failure = e; }
        int awaitSeconds = Math.max(5, temporalProperties.getShutdownAwaitSeconds());
        try { workerFactory.awaitTermination(awaitSeconds, TimeUnit.SECONDS); }
        catch (Exception e) { failure = retain(failure, e); }
        try {
            if (!workerFactory.isTerminated()) {
                workerFactory.shutdownNow();
                workerFactory.awaitTermination(awaitSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) { failure = retain(failure, e); }
        if (failure != null) {
            // Preserve the first failure and cleanup failures while allowing other context owners to close.
            log.warn("Temporal worker shutdown failed; remaining resource cleanup continues", failure);
        }
    }

    private static Exception retain(Exception original, Exception cleanup) {
        if (original == null) return cleanup;
        if (original != cleanup) original.addSuppressed(cleanup);
        return original;
    }
}
