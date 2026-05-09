package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.*;
import com.example.platform.notification.infrastructure.MockNotificationProvider;
import com.example.platform.shared.events.ArtifactCreatedEvent;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.events.RenderJobStatusChangedEvent;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final DSLContext dsl;
    private final List<NotificationProvider> providers;
    private final NotificationRenderingService renderingService;
    private MockNotificationProvider mockProvider;

    public NotificationEventHandler(DSLContext dsl, List<NotificationProvider> providers,
            NotificationRenderingService renderingService) {
        this.dsl = dsl;
        this.providers = providers;
        this.renderingService = renderingService;
    }

    @Autowired(required = false)
    public void setMockNotificationProvider(MockNotificationProvider mockProvider) {
        this.mockProvider = mockProvider;
    }

    @EventListener
    public void onRenderJobCreated(RenderJobCreatedEvent event) {
        log.info("NotificationEventHandler: RenderJobCreatedEvent for job={}", event.renderJobId());
        handle(new NotificationInboundEvent(
                "render.job.created",
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "profile", event.profile(), "backend", event.primaryBackend())
        ));
    }

    @EventListener
    public void onRenderJobStatusChanged(RenderJobStatusChangedEvent event) {
        log.info("NotificationEventHandler: RenderJobStatusChangedEvent for job={}, {} -> {}",
                event.renderJobId(), event.oldStatus(), event.newStatus());
        String eventType = switch (event.newStatus()) {
            case "COMPLETED" -> "render.job.completed";
            case "FAILED" -> "render.job.failed";
            case "AI_PROCESSING" -> "render.job.ai_processing";
            case "RENDERING" -> "render.job.rendering";
            default -> "render.job.status.changed";
        };
        handle(new NotificationInboundEvent(
                eventType,
                event.renderJobId(),
                Map.of("renderJobId", event.renderJobId(), "projectId", event.projectId(),
                        "oldStatus", event.oldStatus(), "newStatus", event.newStatus())
        ));
    }

    @EventListener
    public void onArtifactCreated(ArtifactCreatedEvent event) {
        log.info("NotificationEventHandler: ArtifactCreatedEvent for artifact={}", event.artifactId());
        handle(new NotificationInboundEvent(
                "artifact.created",
                event.artifactId(),
                Map.of("artifactId", event.artifactId(), "renderJobId", event.renderJobId(),
                        "projectId", event.projectId(), "storageUri", event.storageUri())
        ));
    }

    @EventListener
    public void handle(NotificationInboundEvent event) {
        var eventId = Ids.newId("nev");
        dsl.insertInto(table("notification_event"))
                .columns(field("id"), field("event_type"), field("subject_id"), field("payload"), field("created_at"))
                .values(eventId, event.eventType(), event.subjectId(), Jsons.toJson(event.payload()), OffsetDateTime.now())
                .execute();

        var templateCode = NotificationTemplateCode.fromEventType(event.eventType());
        var rendered = renderingService.render(templateCode, event.eventType(), event.subjectId(), event.payload());

        for (var provider : providers) {
            var result = provider.send(new DeliveryCommand(eventId, provider.channel(), rendered.subject(), rendered.body(), Map.of("subjectId", event.subjectId())));
            dsl.insertInto(table("notification_delivery"))
                    .columns(field("id"), field("event_id"), field("channel"), field("provider_code"), field("status"), field("request_payload"), field("response_payload"), field("attempt_count"), field("created_at"))
                    .values(Ids.newId("ndl"), eventId, provider.channel(), provider.providerCode(), result.status(), rendered.body(), result.responsePayload(), 1, OffsetDateTime.now())
                    .execute();
        }
    }
}
