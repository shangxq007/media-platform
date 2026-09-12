package com.example.platform.config;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.notification.app.NotificationEventPublisher;
import com.example.platform.notification.domain.NotificationInboundEvent;
import com.example.platform.notification.app.NotificationOutboxEvents;
import com.example.platform.render.app.event.RenderOutboxEvents;
import com.example.platform.shared.events.*;
import com.example.platform.shared.web.TenantGuard;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Existing publisher-port composition; arbitrary objects never cross the Outbox append boundary. */
@Component
@Primary
public final class OutboxBackedNotificationEventPublisher implements NotificationEventPublisher {
    private final OutboxEventService outbox;
    public OutboxBackedNotificationEventPublisher(OutboxEventService outbox) { this.outbox = outbox; }
    @Override public void publish(Object event) {
        String tenant = TenantGuard.requireTenantId();
        switch (event) {
            case RenderJobCreatedEvent e -> outbox.append(RenderOutboxEvents.RENDERJOBCREATEDEVENT.append(tenant, e, "render.job.created:" + e.renderJobId()));
            case RenderJobStatusChangedEvent e -> outbox.append(RenderOutboxEvents.RENDERJOBSTATUSCHANGEDEVENT.append(tenant, e, "render.job.status.changed:" + e.renderJobId() + ":" + e.oldStatus() + ":" + e.newStatus()));
            case RenderJobCompletedEvent e -> outbox.append(RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT.append(tenant, e, "render.job.completed:" + e.renderJobId()));
            case RenderJobFailedEvent e -> outbox.append(RenderOutboxEvents.RENDERJOBFAILEDEVENT.append(tenant, e, "render.job.failed:" + e.renderJobId()));
            case ArtifactCreatedEvent e -> outbox.append(RenderOutboxEvents.ARTIFACTCREATEDEVENT.append(tenant, e, "artifact.created:" + e.artifactId()));
            case NotificationInboundEvent e -> outbox.append(NotificationOutboxEvents.INBOUND.append(tenant, e, null));
            default -> throw new IllegalArgumentException("Unsupported notification event contract");
        }
    }
}
