package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationSubscription.NOTIFICATION_SUBSCRIPTION;

import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.notification.domain.NotificationSubscription;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                .from(NOTIFICATION_SUBSCRIPTION)
                .where(NOTIFICATION_SUBSCRIPTION.USER_ID.eq(userId))
                .orderBy(NOTIFICATION_SUBSCRIPTION.EVENT_KEY)
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
                .from(NOTIFICATION_SUBSCRIPTION)
                .where(NOTIFICATION_SUBSCRIPTION.USER_ID.eq(userId))
                .and(NOTIFICATION_SUBSCRIPTION.EVENT_KEY.eq(eventKey))
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
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(NOTIFICATION_SUBSCRIPTION)
                .columns(NOTIFICATION_SUBSCRIPTION.ID, NOTIFICATION_SUBSCRIPTION.TENANT_ID, NOTIFICATION_SUBSCRIPTION.USER_ID,
                        NOTIFICATION_SUBSCRIPTION.EVENT_KEY, NOTIFICATION_SUBSCRIPTION.ENABLED, NOTIFICATION_SUBSCRIPTION.CHANNELS,
                        NOTIFICATION_SUBSCRIPTION.FREQUENCY, NOTIFICATION_SUBSCRIPTION.CREATED_AT, NOTIFICATION_SUBSCRIPTION.UPDATED_AT)
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
                enabled, channels, "IMMEDIATE", Map.of(), null, null, null, now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
    }

    public NotificationSubscription updateSubscription(String userId, String eventKey, boolean enabled, List<String> channels) {
        NotificationSubscription existing = findSubscription(userId, eventKey)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_SUBSCRIPTION_NOT_FOUND"),
                        "Subscription not found for event: " + eventKey));

        if (catalogService.isCritical(eventKey) && !enabled) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"),
                    "Critical event cannot be disabled: " + eventKey);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        dsl.update(NOTIFICATION_SUBSCRIPTION)
                .set(NOTIFICATION_SUBSCRIPTION.ENABLED, enabled)
                .set(NOTIFICATION_SUBSCRIPTION.CHANNELS, Jsons.toJson(channels != null && !channels.isEmpty() ? channels : existing.channels()))
                .set(NOTIFICATION_SUBSCRIPTION.UPDATED_AT, now)
                .where(NOTIFICATION_SUBSCRIPTION.ID.eq(existing.subscriptionId()))
                .execute();

        audit.record("USER", "NOTIFICATION_SUBSCRIPTION_UPDATED", "NOTIFICATION",
                "SUBSCRIPTION", existing.subscriptionId(),
                Map.of("userId", userId, "eventKey", eventKey, "enabled", enabled));

        return new NotificationSubscription(existing.subscriptionId(), existing.tenantId(),
                existing.workspaceId(), existing.userId(), existing.eventKey(),
                enabled, channels, existing.frequency(), existing.filters(),
                existing.quietHoursStart(), existing.quietHoursEnd(), existing.quietHoursTimezone(),
                existing.createdAt(), now.atOffset(ZoneOffset.UTC));
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
        String channelsRaw = rec.get(NOTIFICATION_SUBSCRIPTION.CHANNELS);
        List<String> channels = channelsRaw != null && !channelsRaw.isBlank()
                ? Jsons.fromJson(channelsRaw, List.class) : List.of("IN_APP", "EMAIL");

        String filtersRaw = rec.get(NOTIFICATION_SUBSCRIPTION.FILTERS);
        Map<String, String> filters = filtersRaw != null && !filtersRaw.isBlank()
                ? Jsons.fromJson(filtersRaw, Map.class) : Map.of();

        return new NotificationSubscription(
                rec.get(NOTIFICATION_SUBSCRIPTION.ID),
                rec.get(NOTIFICATION_SUBSCRIPTION.TENANT_ID),
                rec.get(NOTIFICATION_SUBSCRIPTION.WORKSPACE_ID),
                rec.get(NOTIFICATION_SUBSCRIPTION.USER_ID),
                rec.get(NOTIFICATION_SUBSCRIPTION.EVENT_KEY),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_SUBSCRIPTION.ENABLED)),
                channels,
                rec.get(NOTIFICATION_SUBSCRIPTION.FREQUENCY),
                filters,
                rec.get(NOTIFICATION_SUBSCRIPTION.QUIET_HOURS_START),
                rec.get(NOTIFICATION_SUBSCRIPTION.QUIET_HOURS_END),
                rec.get(NOTIFICATION_SUBSCRIPTION.QUIET_HOURS_TIMEZONE),
                rec.get(NOTIFICATION_SUBSCRIPTION.CREATED_AT) != null ? rec.get(NOTIFICATION_SUBSCRIPTION.CREATED_AT).atOffset(ZoneOffset.UTC) : null,
                rec.get(NOTIFICATION_SUBSCRIPTION.UPDATED_AT) != null ? rec.get(NOTIFICATION_SUBSCRIPTION.UPDATED_AT).atOffset(ZoneOffset.UTC) : null
        );
    }

    private ConfigurableErrorCode getErrorCode(String code) {
        return errorCodeRegistry.getRequiredErrorCode(code);
    }
}
