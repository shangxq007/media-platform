package com.example.platform.workflow.temporal;

import io.temporal.worker.WorkerFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stops Temporal poll loops and waits for in-flight activities before JVM exit.
 */
@Component
@ConditionalOnBean(WorkerFactory.class)
public class TemporalWorkerGracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerGracefulShutdown.class);

    private final WorkerFactory workerFactory;
    private final AppTemporalProperties temporalProperties;

    public TemporalWorkerGracefulShutdown(WorkerFactory workerFactory, AppTemporalProperties temporalProperties) {
        this.workerFactory = workerFactory;
        this.temporalProperties = temporalProperties;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextClosed(ContextClosedEvent event) {
        log.info("Temporal worker graceful shutdown: initiating WorkerFactory.shutdown()");
        try {
            workerFactory.shutdown();
            int awaitSeconds = Math.max(5, temporalProperties.getShutdownAwaitSeconds());
            workerFactory.awaitTermination(awaitSeconds, TimeUnit.SECONDS);
            log.info(
                    "Temporal worker shutdown finished: isTerminated={} isShutdown={}",
                    workerFactory.isTerminated(),
                    workerFactory.isShutdown());
        } catch (Exception e) {
            log.warn("Temporal worker graceful shutdown error: {}", e.getMessage());
        }
    }
}
