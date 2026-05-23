package com.example.platform.lifecycle;

import com.example.platform.workflow.temporal.AppTemporalProperties;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(WorkerFactory.class)
@ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true")
public class TemporalWorkerHealthIndicator implements HealthIndicator {

    private final WorkerFactory workerFactory;
    private final AppTemporalProperties temporalProperties;

    public TemporalWorkerHealthIndicator(WorkerFactory workerFactory, AppTemporalProperties temporalProperties) {
        this.workerFactory = workerFactory;
        this.temporalProperties = temporalProperties;
    }

    @Override
    public Health health() {
        if (workerFactory.isStarted() && !workerFactory.isShutdown()) {
            return Health.up()
                    .withDetail("namespace", temporalProperties.resolveNamespace())
                    .withDetail("taskQueue", temporalProperties.getTaskQueue())
                    .build();
        }
        return Health.down()
                .withDetail("namespace", temporalProperties.resolveNamespace())
                .withDetail("taskQueue", RenderTaskQueue.NAME)
                .withDetail("started", workerFactory.isStarted())
                .withDetail("shutdown", workerFactory.isShutdown())
                .build();
    }
}
