package com.example.platform.outbox.app;

import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCompletedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobFailedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class OutboxBackedNotificationEventPublisher implements NotificationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxBackedNotificationEventPublisher.class);

    private final OutboxEventService outboxEventService;

    public OutboxBackedNotificationEventPublisher(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    @Override
    public void publish(Object event) {
        if (event instanceof RenderJobCreatedEvent e) {
            String idemKey = "render.job.created:" + e.renderJobId();
            String id = outboxEventService.appendEvent(
                    "render_job",
                    e.renderJobId(),
                    "render.job.created",
                    1,
                    Map.of(
                            "renderJobId", e.renderJobId(),
                            "projectId", e.projectId(),
                            "timelineSnapshotId", e.timelineSnapshotId(),
                            "profile", e.profile(),
                            "primaryBackend", e.primaryBackend()
                    ),
                    idemKey
            );
            log.debug("Appended render.job.created outbox event {} with idempotency key {}", id, idemKey);
            return;
        }
        if (event instanceof RenderJobStatusChangedEvent e) {
            String idemKey = "render.job.status.changed:" + e.renderJobId() + ":" + e.oldStatus() + ":" + e.newStatus();
            String id = outboxEventService.appendEvent(
                    "render_job",
                    e.renderJobId(),
                    "render.job.status.changed",
                    1,
                    Map.of(
                            "renderJobId", e.renderJobId(),
                            "projectId", e.projectId(),
                            "oldStatus", e.oldStatus(),
                            "newStatus", e.newStatus()
                    ),
                    idemKey
            );
            log.debug("Appended render.job.status.changed outbox event {} with idempotency key {}", id, idemKey);
            return;
        }
        if (event instanceof RenderJobCompletedEvent e) {
            String idemKey = "render.job.completed:" + e.renderJobId();
            String id = outboxEventService.appendEvent(
                    "render_job",
                    e.renderJobId(),
                    "render.job.completed",
                    1,
                    Map.of(
                            "renderJobId", e.renderJobId(),
                            "projectId", e.projectId(),
                            "artifactId", e.artifactId(),
                            "storageUri", e.storageUri()
                    ),
                    idemKey
            );
            log.debug("Appended render.job.completed outbox event {} with idempotency key {}", id, idemKey);
            return;
        }
        if (event instanceof RenderJobFailedEvent e) {
            String idemKey = "render.job.failed:" + e.renderJobId();
            String id = outboxEventService.appendEvent(
                    "render_job",
                    e.renderJobId(),
                    "render.job.failed",
                    1,
                    Map.of(
                            "renderJobId", e.renderJobId(),
                            "projectId", e.projectId(),
                            "error", e.error()
                    ),
                    idemKey
            );
            log.debug("Appended render.job.failed outbox event {} with idempotency key {}", id, idemKey);
            return;
        }
        if (event instanceof ArtifactCreatedEvent e) {
            String idemKey = "artifact.created:" + e.artifactId();
            String id = outboxEventService.appendEvent(
                    "artifact",
                    e.artifactId(),
                    "artifact.created",
                    1,
                    Map.of(
                            "artifactId", e.artifactId(),
                            "renderJobId", e.renderJobId(),
                            "projectId", e.projectId(),
                            "storageUri", e.storageUri()
                    ),
                    idemKey
            );
            log.debug("Appended artifact.created outbox event {} with idempotency key {}", id, idemKey);
            return;
        }

        // Generic fallback — no idempotency key for unknown events
        outboxEventService.appendEvent(
                "generic",
                "n/a",
                event.getClass().getName(),
                1,
                Map.of("toString", String.valueOf(event))
        );
    }
}
