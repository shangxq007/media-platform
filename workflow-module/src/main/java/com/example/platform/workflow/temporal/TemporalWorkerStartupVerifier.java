package com.example.platform.workflow.temporal;

import io.temporal.worker.WorkerFactory;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs Temporal worker / namespace binding at startup; optionally fails when worker is required but missing.
 */
@Component
@ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true")
public class TemporalWorkerStartupVerifier {

    private static final Logger log = LoggerFactory.getLogger(TemporalWorkerStartupVerifier.class);

    private final AppTemporalProperties properties;
    private final Optional<WorkerFactory> workerFactory;

    public TemporalWorkerStartupVerifier(
            AppTemporalProperties properties,
            @Autowired(required = false) WorkerFactory workerFactory) {
        this.properties = properties;
        this.workerFactory = Optional.ofNullable(workerFactory);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        String namespace = properties.resolveNamespace();
        String taskQueue = properties.getTaskQueue();
        if (workerFactory.isPresent()) {
            WorkerFactory factory = workerFactory.get();
            log.info(
                    "Temporal worker ready: namespace={} taskQueue={} started={} shutdown={}",
                    namespace,
                    taskQueue,
                    factory.isStarted(),
                    factory.isShutdown());
            return;
        }
        String message = "Temporal enabled but WorkerFactory bean is missing; ensure profile 'temporal' "
                + "and spring.temporal.workers are configured";
        if (properties.isWorkerRequired()) {
            log.error("{} (namespace={})", message, namespace);
        } else {
            log.warn("{} (namespace={})", message, namespace);
        }
        if (properties.isFailOnMissingWorker()) {
            throw new IllegalStateException(message);
        }
    }
}
