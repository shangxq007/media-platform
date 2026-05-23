package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.*;
import com.example.platform.notification.infrastructure.NotificationProviderRouter;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.notification.NotificationEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringNotificationEventPublisher implements NotificationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SpringNotificationEventPublisher.class);

    private final ApplicationEventPublisher publisher;
    private final DSLContext dsl;
    private final NotificationEventCatalogService catalogService;
    private final NotificationSubscriptionService subscriptionService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationInboxService inboxService;
    private final NotificationProviderRouter providerRouter;
    private final NotificationRenderingService renderingService;
    private final AuditPort audit;

    public SpringNotificationEventPublisher(ApplicationEventPublisher publisher, DSLContext dsl,
            NotificationEventCatalogService catalogService,
            NotificationSubscriptionService subscriptionService,
            NotificationPreferenceService preferenceService,
            NotificationInboxService inboxService,
            NotificationProviderRouter providerRouter,
            NotificationRenderingService renderingService,
            AuditPort audit) {
        this.publisher = publisher;
        this.dsl = dsl;
        this.catalogService = catalogService;
        this.subscriptionService = subscriptionService;
        this.preferenceService = preferenceService;
        this.inboxService = inboxService;
        this.providerRouter = providerRouter;
        this.renderingService = renderingService;
        this.audit = audit;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }

    public void publishToUser(String userId, String eventKey, Map<String, Object> payload) {
        NotificationEventDefinition eventDef = catalogService.findByKey(eventKey).orElse(null);
        if (eventDef == null) {
            log.warn("SpringNotificationEventPublisher: event definition not found for key={}", eventKey);
            return;
        }

        NotificationPreference preference = preferenceService.getPreferences(userId);
        if (!preference.globalEnabled() && !eventDef.critical()) {
            log.debug("SpringNotificationEventPublisher: notifications globally disabled for user={}", userId);
            return;
        }

        NotificationSubscription subscription = subscriptionService.findSubscription(userId, eventKey).orElse(null);
        boolean subscribed = subscription == null ? eventDef.defaultEnabled() : subscription.enabled();
        if (!subscribed && !eventDef.critical()) {
            log.debug("SpringNotificationEventPublisher: user={} not subscribed to event={}", userId, eventKey);
            return;
        }

        List<String> targetChannels;
        if (subscription != null && subscription.channels() != null && !subscription.channels().isEmpty()) {
            targetChannels = subscription.channels();
        } else {
            targetChannels = eventDef.supportedChannels();
        }

        List<String> effectiveChannels = targetChannels.stream()
                .filter(ch -> {
                    Boolean channelOn = preference.channelEnabled().get(ch);
                    return channelOn == null || channelOn;
                })
                .filter(ch -> {
                    if (eventDef.critical()) return true;
                    Boolean eventOn = preference.eventEnabled().get(eventKey);
                    return eventOn == null || eventOn;
                })
                .toList();

        if (effectiveChannels.isEmpty()) {
            log.debug("SpringNotificationEventPublisher: no effective channels for user={}, event={}", userId, eventKey);
            return;
        }

        String eventId = Ids.newId("nev");
        String tenantId = com.example.platform.shared.web.TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(table("notification_event"))
                .columns(field("id"), field("event_type"), field("subject_id"), field("payload"), field("created_at"))
                .values(eventId, eventKey, userId, Jsons.toJson(payload), now)
                .execute();

        var templateCode = NotificationTemplateCode.fromEventType(eventKey);
        var rendered = renderingService.render(templateCode, eventKey, userId, payload);

        for (String channel : effectiveChannels) {
            deliver(eventId, eventKey, tenantId, userId, channel, rendered.subject(), rendered.body(), payload);
        }

        if (effectiveChannels.contains("IN_APP")) {
            String type = mapSeverityToType(eventDef.severity());
            inboxService.createInboxItem(userId, eventKey, type,
                    rendered.subject(), rendered.body(),
                    payload != null ? (String) payload.get("link") : null,
                    payload != null ? (String) payload.get("actorId") : null,
                    payload != null ? (String) payload.get("resourceType") : null,
                    payload != null ? (String) payload.get("resourceId") : null);
        }
    }

    private void deliver(String eventId, String eventKey, String tenantId, String userId,
            String channel, String subject, String body, Map<String, Object> payload) {
        String deliveryId = Ids.newId("ndr");
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(table("notification_delivery_record"))
                .columns(field("id"), field("event_key"), field("tenant_id"), field("user_id"),
                        field("channel_type"), field("status"), field("attempts"),
                        field("payload_redacted"), field("created_at"))
                .values(deliveryId, eventKey, tenantId, userId,
                        channel, "PENDING", 0,
                        body != null && body.length() > 200 ? body.substring(0, 200) : body,
                        now)
                .execute();

        audit.record("SYSTEM", "NOTIFICATION_DELIVERY_CREATED", "NOTIFICATION",
                "DELIVERY_RECORD", deliveryId,
                Map.of("eventKey", eventKey, "userId", userId, "channel", channel));

        try {
            DeliveryCommand command = new DeliveryCommand(eventId, channel,
                    subject != null ? subject : eventKey,
                    body != null ? body : Jsons.toJson(payload),
                    Map.of("subjectId", userId, "novuWorkflowId",
                            catalogService.findByKey(eventKey)
                                    .map(NotificationEventDefinition::novuWorkflowId).orElse(null),
                            "subscriberId", userId));

            DeliveryResult result = providerRouter.route(command, channel);

            if ("SENT".equals(result.status())) {
                dsl.update(table("notification_delivery_record"))
                        .set(field("status"), "SENT")
                        .set(field("attempts"), 1)
                        .set(field("sent_at"), OffsetDateTime.now())
                        .set(field("provider_message_id"), extractMessageId(result.responsePayload()))
                        .where(field("id").eq(deliveryId))
                        .execute();

                audit.record("SYSTEM", "NOTIFICATION_DELIVERY_SENT", "NOTIFICATION",
                        "DELIVERY_RECORD", deliveryId,
                        Map.of("eventKey", eventKey, "userId", userId, "channel", channel));
            } else {
                dsl.update(table("notification_delivery_record"))
                        .set(field("status"), "FAILED")
                        .set(field("attempts"), 1)
                        .set(field("failed_at"), OffsetDateTime.now())
                        .set(field("error_code"), "NOTIFICATION_DELIVERY_FAILED")
                        .where(field("id").eq(deliveryId))
                        .execute();

                audit.record("SYSTEM", "NOTIFICATION_DELIVERY_FAILED", "NOTIFICATION",
                        "DELIVERY_RECORD", deliveryId,
                        Map.of("eventKey", eventKey, "userId", userId, "channel", channel,
                                "error", result.responsePayload()));
            }
        } catch (Exception e) {
            log.error("SpringNotificationEventPublisher: delivery failed for deliveryId={}, error={}", deliveryId, e.getMessage());
            dsl.update(table("notification_delivery_record"))
                    .set(field("status"), "FAILED")
                    .set(field("attempts"), 1)
                    .set(field("failed_at"), OffsetDateTime.now())
                    .set(field("error_code"), "NOTIFICATION_DELIVERY_FAILED")
                    .where(field("id").eq(deliveryId))
                    .execute();

            audit.record("SYSTEM", "NOTIFICATION_DELIVERY_FAILED", "NOTIFICATION",
                    "DELIVERY_RECORD", deliveryId,
                    Map.of("eventKey", eventKey, "userId", userId, "channel", channel,
                            "error", e.getMessage()));
        }
    }

    private String mapSeverityToType(String severity) {
        if (severity == null) return "INFO";
        return switch (severity) {
            case "WARNING" -> "WARNING";
            case "ERROR" -> "ERROR";
            case "CRITICAL" -> "ERROR";
            default -> "INFO";
        };
    }

    private String extractMessageId(String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) return null;
        try {
            Map<?, ?> map = Jsons.fromJson(responsePayload, Map.class);
            Object id = map.get("id");
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
