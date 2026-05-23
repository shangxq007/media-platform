package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.notification.domain.NotificationSubscription;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(NotificationSubscriptionService.class);

    private final DSLContext dsl;
    private final AuditPort audit;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final NotificationEventCatalogService catalogService;

    public NotificationSubscriptionService(DSLContext dsl, AuditPort audit,
            ErrorCodeRegistry errorCodeRegistry, NotificationEventCatalogService catalogService) {
        this.dsl = dsl;
        this.audit = audit;
        this.errorCodeRegistry = errorCodeRegistry;
        this.catalogService = catalogService;
    }

    public List<NotificationSubscription> listUserSubscriptions(String userId) {
        return dsl.select()
                .from(table("notification_subscription"))
                .where(field("user_id").eq(userId))
                .orderBy(field("event_key"))
                .fetch(this::mapRecord);
    }

    public List<NotificationSubscription> listSubscribableEvents(String userId) {
        List<NotificationEventDefinition> configurableEvents = catalogService.listUserConfigurableEvents();
        return configurableEvents.stream()
                .filter(e -> catalogService.isSubscribable(e.eventKey()))
                .map(e -> {
                    Optional<NotificationSubscription> existing = findSubscription(userId, e.eventKey());
                    return existing.orElseGet(() -> new NotificationSubscription(
                            null, null, null, userId, e.eventKey(),
                            e.defaultEnabled(), e.supportedChannels(),
                            "IMMEDIATE", Map.of(), null, null, null,
                            null, null
                    ));
                })
                .toList();
    }

    public Optional<NotificationSubscription> findSubscription(String userId, String eventKey) {
        var rec = dsl.select()
                .from(table("notification_subscription"))
                .where(field("user_id").eq(userId))
                .and(field("event_key").eq(eventKey))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    public NotificationSubscription upsertSubscription(String userId, String eventKey, boolean enabled, List<String> channels) {
        if (!catalogService.isSubscribable(eventKey)) {
            throw new PlatformException(getErrorCode("NOTIFICATION_EVENT_NOT_SUBSCRIBABLE"),
                    "Event is not subscribable: " + eventKey);
        }

        if (catalogService.isCritical(eventKey) && !enabled) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"),
                    "Critical event cannot be disabled: " + eventKey);
        }

        Optional<NotificationSubscription> existing = findSubscription(userId, eventKey);
        if (existing.isPresent()) {
            return updateSubscription(userId, eventKey, enabled, channels);
        }
        return createSubscription(userId, eventKey, enabled, channels);
    }

    public NotificationSubscription createSubscription(String userId, String eventKey, boolean enabled, List<String> channels) {
        if (!catalogService.isSubscribable(eventKey)) {
            throw new PlatformException(getErrorCode("NOTIFICATION_EVENT_NOT_SUBSCRIBABLE"),
                    "Event is not subscribable: " + eventKey);
        }

        if (catalogService.isCritical(eventKey) && !enabled) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"),
                    "Critical event cannot be disabled: " + eventKey);
        }

        String subscriptionId = Ids.newId("nsu");
        String tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(table("notification_subscription"))
                .columns(field("id"), field("tenant_id"), field("user_id"),
                        field("event_key"), field("enabled"), field("channels"),
                        field("frequency"), field("created_at"), field("updated_at"))
                .values(subscriptionId, tenantId, userId,
                        eventKey, enabled,
                        Jsons.toJson(channels != null && !channels.isEmpty() ? channels : List.of("IN_APP", "EMAIL")),
                        "IMMEDIATE", now, now)
                .execute();

        audit.record("USER", "NOTIFICATION_SUBSCRIPTION_CREATED", "NOTIFICATION",
                "SUBSCRIPTION", subscriptionId,
                Map.of("userId", userId, "eventKey", eventKey, "enabled", enabled));

        log.info("NotificationSubscriptionService: created subscription for user={}, event={}, enabled={}", userId, eventKey, enabled);
        return new NotificationSubscription(subscriptionId, tenantId, null, userId, eventKey,
                enabled, channels, "IMMEDIATE", Map.of(), null, null, null, now, now);
    }

    public NotificationSubscription updateSubscription(String userId, String eventKey, boolean enabled, List<String> channels) {
        NotificationSubscription existing = findSubscription(userId, eventKey)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_SUBSCRIPTION_NOT_FOUND"),
                        "Subscription not found for event: " + eventKey));

        if (catalogService.isCritical(eventKey) && !enabled) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"),
                    "Critical event cannot be disabled: " + eventKey);
        }

        OffsetDateTime now = OffsetDateTime.now();
        dsl.update(table("notification_subscription"))
                .set(field("enabled"), enabled)
                .set(field("channels"), Jsons.toJson(channels != null && !channels.isEmpty() ? channels : existing.channels()))
                .set(field("updated_at"), now)
                .where(field("id").eq(existing.subscriptionId()))
                .execute();

        audit.record("USER", "NOTIFICATION_SUBSCRIPTION_UPDATED", "NOTIFICATION",
                "SUBSCRIPTION", existing.subscriptionId(),
                Map.of("userId", userId, "eventKey", eventKey, "enabled", enabled));

        return new NotificationSubscription(existing.subscriptionId(), existing.tenantId(),
                existing.workspaceId(), existing.userId(), existing.eventKey(),
                enabled, channels, existing.frequency(), existing.filters(),
                existing.quietHoursStart(), existing.quietHoursEnd(), existing.quietHoursTimezone(),
                existing.createdAt(), now);
    }

    public List<NotificationSubscription> batchUpdate(String userId, List<Map<String, Object>> updates) {
        return updates.stream()
                .map(update -> {
                    String eventKey = (String) update.get("eventKey");
                    boolean enabled = Boolean.TRUE.equals(update.get("enabled"));
                    @SuppressWarnings("unchecked")
                    List<String> channels = (List<String>) update.get("channels");
                    return upsertSubscription(userId, eventKey, enabled, channels);
                })
                .toList();
    }

    private NotificationSubscription mapRecord(org.jooq.Record rec) {
        String channelsRaw = rec.get(field("channels"), String.class);
        List<String> channels = channelsRaw != null && !channelsRaw.isBlank()
                ? Jsons.fromJson(channelsRaw, List.class) : List.of("IN_APP", "EMAIL");

        String filtersRaw = rec.get(field("filters"), String.class);
        Map<String, String> filters = filtersRaw != null && !filtersRaw.isBlank()
                ? Jsons.fromJson(filtersRaw, Map.class) : Map.of();

        return new NotificationSubscription(
                rec.get(field("id"), String.class),
                rec.get(field("tenant_id"), String.class),
                rec.get(field("workspace_id"), String.class),
                rec.get(field("user_id"), String.class),
                rec.get(field("event_key"), String.class),
                Boolean.TRUE.equals(rec.get(field("enabled"), Boolean.class)),
                channels,
                rec.get(field("frequency"), String.class),
                filters,
                rec.get(field("quiet_hours_start"), String.class),
                rec.get(field("quiet_hours_end"), String.class),
                rec.get(field("quiet_hours_timezone"), String.class),
                rec.get(field("created_at"), OffsetDateTime.class),
                rec.get(field("updated_at"), OffsetDateTime.class)
        );
    }

    private ConfigurableErrorCode getErrorCode(String code) {
        return errorCodeRegistry.getRequiredErrorCode(code);
    }
}
