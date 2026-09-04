package com.example.platform.notification.app;

import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.shared.Ids;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.NotificationEventDefinition.NOTIFICATION_EVENT_DEFINITION;


@Service
public class NotificationEventCatalogService {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventCatalogService.class);

    private static final List<String> ALL_CHANNELS = List.of("IN_APP", "EMAIL", "SMS", "WEBHOOK");

    private final DSLContext dsl;
    private final Map<String, NotificationEventDefinition> eventCache = new ConcurrentHashMap<>();

    public NotificationEventCatalogService(DSLContext dsl) {
        this.dsl = dsl;
    }

    private volatile boolean seeded = false;

    /** Seeds built-in event definitions (idempotent). Used at startup and in tests. */
    public void init() {
        ensureSeeded();
    }

    private void ensureSeeded() {
        if (!seeded) {
            synchronized (this) {
                if (!seeded) {
                    seedBuiltInEvents();
                    seeded = true;
                }
            }
        }
    }

    public List<NotificationEventDefinition> listAllEvents() {
        ensureSeeded();
        return dsl.select()
                .from(NOTIFICATION_EVENT_DEFINITION)
                .where(NOTIFICATION_EVENT_DEFINITION.ARCHIVED.eq(false))
                .orderBy(NOTIFICATION_EVENT_DEFINITION.CATEGORY, NOTIFICATION_EVENT_DEFINITION.NAME)
                .fetch(this::mapRecord);
    }

    public List<NotificationEventDefinition> listUserConfigurableEvents() {
        ensureSeeded();
        return dsl.select()
                .from(NOTIFICATION_EVENT_DEFINITION)
                .where(NOTIFICATION_EVENT_DEFINITION.ARCHIVED.eq(false))
                .and(NOTIFICATION_EVENT_DEFINITION.USER_CONFIGURABLE.eq(true))
                .orderBy(NOTIFICATION_EVENT_DEFINITION.CATEGORY, NOTIFICATION_EVENT_DEFINITION.NAME)
                .fetch(this::mapRecord);
    }

    public List<NotificationEventDefinition> listEventsByCategory(String category) {
        ensureSeeded();
        return dsl.select()
                .from(NOTIFICATION_EVENT_DEFINITION)
                .where(NOTIFICATION_EVENT_DEFINITION.ARCHIVED.eq(false))
                .and(NOTIFICATION_EVENT_DEFINITION.CATEGORY.eq(category))
                .orderBy(NOTIFICATION_EVENT_DEFINITION.NAME)
                .fetch(this::mapRecord);
    }

    public Optional<NotificationEventDefinition> findByKey(String eventKey) {
        ensureSeeded();
        var rec = dsl.select()
                .from(NOTIFICATION_EVENT_DEFINITION)
                .where(NOTIFICATION_EVENT_DEFINITION.EVENT_KEY.eq(eventKey))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    public NotificationEventDefinition getRequired(String eventKey) {
        return findByKey(eventKey).orElseThrow(() ->
                new IllegalArgumentException("Notification event not found: " + eventKey));
    }

    public NotificationEventDefinition create(NotificationEventDefinition definition) {
        String id = Ids.newId("nevdef");
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(NOTIFICATION_EVENT_DEFINITION)
                .columns(NOTIFICATION_EVENT_DEFINITION.ID, NOTIFICATION_EVENT_DEFINITION.EVENT_KEY, NOTIFICATION_EVENT_DEFINITION.NAME, NOTIFICATION_EVENT_DEFINITION.DESCRIPTION,
                        NOTIFICATION_EVENT_DEFINITION.CATEGORY, NOTIFICATION_EVENT_DEFINITION.SEVERITY, NOTIFICATION_EVENT_DEFINITION.VISIBILITY,
                        NOTIFICATION_EVENT_DEFINITION.USER_CONFIGURABLE, NOTIFICATION_EVENT_DEFINITION.CRITICAL, NOTIFICATION_EVENT_DEFINITION.DEFAULT_ENABLED,
                        NOTIFICATION_EVENT_DEFINITION.SUPPORTED_CHANNELS, NOTIFICATION_EVENT_DEFINITION.REQUIRED_PERMISSIONS,
                        NOTIFICATION_EVENT_DEFINITION.REQUIRED_ENTITLEMENTS, NOTIFICATION_EVENT_DEFINITION.FEATURE_FLAG_KEY,
                        NOTIFICATION_EVENT_DEFINITION.NOVU_WORKFLOW_ID, NOTIFICATION_EVENT_DEFINITION.LOCAL_TEMPLATE_KEY,
                        NOTIFICATION_EVENT_DEFINITION.ARCHIVED, NOTIFICATION_EVENT_DEFINITION.CREATED_AT, NOTIFICATION_EVENT_DEFINITION.UPDATED_AT)
                .values(id, definition.eventKey(), definition.name(), definition.description(),
                        definition.category(), definition.severity(), definition.visibility(),
                        definition.userConfigurable(), definition.critical(), definition.defaultEnabled(),
                        NotificationPayloadJson.toJson(definition.supportedChannels()), NotificationPayloadJson.toJson(definition.requiredPermissions()),
                        NotificationPayloadJson.toJson(definition.requiredEntitlements()), definition.featureFlagKey(),
                        definition.novuWorkflowId(), definition.localTemplateKey(),
                        false, now, now)
                .execute();
        NotificationEventDefinition saved = new NotificationEventDefinition(
                definition.eventKey(), definition.name(), definition.description(),
                definition.category(), definition.severity(), definition.visibility(),
                definition.userConfigurable(), definition.critical(), definition.defaultEnabled(),
                definition.supportedChannels(), definition.requiredPermissions(),
                definition.requiredEntitlements(), definition.featureFlagKey(),
                definition.novuWorkflowId(), definition.localTemplateKey(),
                false, now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        eventCache.put(definition.eventKey(), saved);
        return saved;
    }

    public NotificationEventDefinition update(String eventKey, NotificationEventDefinition definition) {
        LocalDateTime now = LocalDateTime.now();
        dsl.update(NOTIFICATION_EVENT_DEFINITION)
                .set(NOTIFICATION_EVENT_DEFINITION.NAME, definition.name())
                .set(NOTIFICATION_EVENT_DEFINITION.DESCRIPTION, definition.description())
                .set(NOTIFICATION_EVENT_DEFINITION.CATEGORY, definition.category())
                .set(NOTIFICATION_EVENT_DEFINITION.SEVERITY, definition.severity())
                .set(NOTIFICATION_EVENT_DEFINITION.VISIBILITY, definition.visibility())
                .set(NOTIFICATION_EVENT_DEFINITION.USER_CONFIGURABLE, definition.userConfigurable())
                .set(NOTIFICATION_EVENT_DEFINITION.CRITICAL, definition.critical())
                .set(NOTIFICATION_EVENT_DEFINITION.DEFAULT_ENABLED, definition.defaultEnabled())
                .set(NOTIFICATION_EVENT_DEFINITION.SUPPORTED_CHANNELS, NotificationPayloadJson.toJson(definition.supportedChannels()))
                .set(NOTIFICATION_EVENT_DEFINITION.REQUIRED_PERMISSIONS, NotificationPayloadJson.toJson(definition.requiredPermissions()))
                .set(NOTIFICATION_EVENT_DEFINITION.REQUIRED_ENTITLEMENTS, NotificationPayloadJson.toJson(definition.requiredEntitlements()))
                .set(NOTIFICATION_EVENT_DEFINITION.FEATURE_FLAG_KEY, definition.featureFlagKey())
                .set(NOTIFICATION_EVENT_DEFINITION.NOVU_WORKFLOW_ID, definition.novuWorkflowId())
                .set(NOTIFICATION_EVENT_DEFINITION.LOCAL_TEMPLATE_KEY, definition.localTemplateKey())
                .set(NOTIFICATION_EVENT_DEFINITION.UPDATED_AT, now)
                .where(NOTIFICATION_EVENT_DEFINITION.EVENT_KEY.eq(eventKey))
                .execute();
        NotificationEventDefinition updated = new NotificationEventDefinition(
                definition.eventKey(), definition.name(), definition.description(),
                definition.category(), definition.severity(), definition.visibility(),
                definition.userConfigurable(), definition.critical(), definition.defaultEnabled(),
                definition.supportedChannels(), definition.requiredPermissions(),
                definition.requiredEntitlements(), definition.featureFlagKey(),
                definition.novuWorkflowId(), definition.localTemplateKey(),
                definition.archived(), definition.createdAt(), now.atOffset(ZoneOffset.UTC));
        eventCache.put(eventKey, updated);
        return updated;
    }

    public void archive(String eventKey) {
        LocalDateTime now = LocalDateTime.now();
        dsl.update(NOTIFICATION_EVENT_DEFINITION)
                .set(NOTIFICATION_EVENT_DEFINITION.ARCHIVED, true)
                .set(NOTIFICATION_EVENT_DEFINITION.UPDATED_AT, now)
                .where(NOTIFICATION_EVENT_DEFINITION.EVENT_KEY.eq(eventKey))
                .execute();
        NotificationEventDefinition existing = eventCache.get(eventKey);
        if (existing != null) {
            eventCache.put(eventKey, new NotificationEventDefinition(
                    existing.eventKey(), existing.name(), existing.description(),
                    existing.category(), existing.severity(), existing.visibility(),
                    existing.userConfigurable(), existing.critical(), existing.defaultEnabled(),
                    existing.supportedChannels(), existing.requiredPermissions(),
                    existing.requiredEntitlements(), existing.featureFlagKey(),
                    existing.novuWorkflowId(), existing.localTemplateKey(),
                    true, existing.createdAt(), now.atOffset(ZoneOffset.UTC)));
        }
    }

    public List<String> getSupportedChannels(String eventKey) {
        return findByKey(eventKey)
                .map(NotificationEventDefinition::supportedChannels)
                .filter(channels -> channels != null && !channels.isEmpty())
                .orElse(ALL_CHANNELS);
    }

    public boolean isUserConfigurable(String eventKey) {
        return findByKey(eventKey)
                .map(NotificationEventDefinition::userConfigurable)
                .orElse(false);
    }

    public boolean isCritical(String eventKey) {
        return findByKey(eventKey)
                .map(NotificationEventDefinition::critical)
                .orElse(false);
    }

    public boolean isSubscribable(String eventKey) {
        return findByKey(eventKey)
                .map(def -> def.userConfigurable() && !"SYSTEM_ONLY".equals(def.visibility()))
                .orElse(false);
    }

    private void seedBuiltInEvents() {
        List<NotificationEventDefinition> builtInEvents = List.of(
                builtin("render.job.completed", "Render Job Completed", "A render job has finished successfully",
                        "RENDER", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("render.job.failed", "Render Job Failed", "A render job has failed",
                        "RENDER", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("render.job.requires_review", "Render Job Requires Review", "A render job requires manual review",
                        "RENDER", "WARNING", "ADMIN_CONTROLLED", false, false, true),
                builtin("render.job.cancelled", "Render Job Cancelled", "A render job was cancelled",
                        "RENDER", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("render.cache.hash_invalidated", "Render Cache Hash Invalidated",
                        "Incremental reuse dropped cache entries due to content-hash mismatch",
                        "RENDER", "WARNING", "USER_CONFIGURABLE", true, false, true),
                builtin("render.delivery.completed", "Render Delivery Completed",
                        "Final artifact delivered to configured destination",
                        "RENDER", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("render.delivery.failed", "Render Delivery Failed",
                        "Artifact delivery to external storage failed",
                        "RENDER", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("quota.usage.warning", "Quota Usage Warning", "Approaching quota limit",
                        "QUOTA", "WARNING", "USER_CONFIGURABLE", true, false, true),
                builtin("quota.exceeded", "Quota Exceeded", "Quota limit has been exceeded",
                        "QUOTA", "ERROR", "CRITICAL", false, true, true),
                builtin("credits.low", "Low Credits", "Account credits are running low",
                        "BILLING", "WARNING", "USER_CONFIGURABLE", true, false, true),
                builtin("billing.invoice.generated", "Invoice Generated", "A new invoice has been generated",
                        "BILLING", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("billing.payment.failed", "Payment Failed", "A payment attempt has failed",
                        "BILLING", "ERROR", "CRITICAL", false, true, true),
                builtin("entitlement.granted", "Entitlement Granted", "An entitlement has been granted",
                        "ENTITLEMENT", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("entitlement.revoked", "Entitlement Revoked", "An entitlement has been revoked",
                        "ENTITLEMENT", "WARNING", "CRITICAL", false, true, true),
                builtin("resource.shared", "Resource Shared", "A resource has been shared with you",
                        "RESOURCE", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("resource.invite.received", "Resource Invite Received", "You received a resource invitation",
                        "RESOURCE", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("feedback.updated", "Feedback Updated", "Feedback has been updated",
                        "FEEDBACK", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("report.completed", "Report Completed", "A report has been generated successfully",
                        "REPORT", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("report.failed", "Report Failed", "A report generation has failed",
                        "REPORT", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("nlq.query.failed", "NLQ Query Failed", "A natural language query has failed",
                        "REPORT", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("prompt.execution.completed", "Prompt Execution Completed", "A prompt execution has completed",
                        "SYSTEM", "INFO", "USER_CONFIGURABLE", true, false, true),
                builtin("prompt.execution.failed", "Prompt Execution Failed", "A prompt execution has failed",
                        "SYSTEM", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("prompt.risk_review_required", "Prompt Risk Review Required", "A prompt requires risk review",
                        "SYSTEM", "WARNING", "ADMIN_CONTROLLED", false, false, true),
                builtin("extension.execution.failed", "Extension Execution Failed", "An extension execution has failed",
                        "SYSTEM", "ERROR", "USER_CONFIGURABLE", true, false, true),
                builtin("provider.health.degraded", "Provider Health Degraded", "A provider's health has degraded",
                        "PROVIDER", "WARNING", "ADMIN_CONTROLLED", false, false, true),
                builtin("worker.offline", "Worker Offline", "A worker node has gone offline",
                        "WORKER", "ERROR", "ADMIN_CONTROLLED", false, false, true),
                builtin("security.suspicious_activity", "Suspicious Activity", "Suspicious activity detected",
                        "SECURITY", "CRITICAL", "CRITICAL", false, true, true),
                builtin("system.announcement", "System Announcement", "A system-wide announcement",
                        "SYSTEM", "INFO", "SYSTEM_ONLY", false, false, true)
        );

        for (NotificationEventDefinition event : builtInEvents) {
            boolean exists = dsl.fetchExists(
                    dsl.selectOne().from(NOTIFICATION_EVENT_DEFINITION)
                            .where(NOTIFICATION_EVENT_DEFINITION.EVENT_KEY.eq(event.eventKey()))
            );
            if (!exists) {
                create(event);
                log.info("Seeded notification event definition: {}", event.eventKey());
            } else {
                eventCache.put(event.eventKey(), event);
            }
        }
    }

    private static NotificationEventDefinition builtin(String key, String name, String description,
            String category, String severity, String visibility,
            boolean userConfigurable, boolean critical, boolean defaultEnabled) {
        OffsetDateTime now = OffsetDateTime.now();
        return new NotificationEventDefinition(
                key, name, description, category, severity, visibility,
                userConfigurable, critical, defaultEnabled,
                ALL_CHANNELS, List.of(), List.of(),
                null, null, null, false, now, now
        );
    }

    @SuppressWarnings("unchecked")
    private NotificationEventDefinition mapRecord(org.jooq.Record rec) {
        String supportedChannelsRaw = rec.get(NOTIFICATION_EVENT_DEFINITION.SUPPORTED_CHANNELS, String.class);
        List<String> supportedChannels = supportedChannelsRaw != null && !supportedChannelsRaw.isBlank()
                ? NotificationPayloadJson.fromJson(supportedChannelsRaw, List.class) : ALL_CHANNELS;

        String requiredPermsRaw = rec.get(NOTIFICATION_EVENT_DEFINITION.REQUIRED_PERMISSIONS, String.class);
        List<String> requiredPerms = requiredPermsRaw != null && !requiredPermsRaw.isBlank()
                ? NotificationPayloadJson.fromJson(requiredPermsRaw, List.class) : List.of();

        String requiredEntitlementsRaw = rec.get(NOTIFICATION_EVENT_DEFINITION.REQUIRED_ENTITLEMENTS, String.class);
        List<String> requiredEntitlements = requiredEntitlementsRaw != null && !requiredEntitlementsRaw.isBlank()
                ? NotificationPayloadJson.fromJson(requiredEntitlementsRaw, List.class) : List.of();

        return new NotificationEventDefinition(
                rec.get(NOTIFICATION_EVENT_DEFINITION.EVENT_KEY, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.NAME, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.DESCRIPTION, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.CATEGORY, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.SEVERITY, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.VISIBILITY, String.class),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_EVENT_DEFINITION.USER_CONFIGURABLE, Boolean.class)),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_EVENT_DEFINITION.CRITICAL, Boolean.class)),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_EVENT_DEFINITION.DEFAULT_ENABLED, Boolean.class)),
                supportedChannels,
                requiredPerms,
                requiredEntitlements,
                rec.get(NOTIFICATION_EVENT_DEFINITION.FEATURE_FLAG_KEY, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.NOVU_WORKFLOW_ID, String.class),
                rec.get(NOTIFICATION_EVENT_DEFINITION.LOCAL_TEMPLATE_KEY, String.class),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_EVENT_DEFINITION.ARCHIVED, Boolean.class)),
                toOffset(rec.get(NOTIFICATION_EVENT_DEFINITION.CREATED_AT, LocalDateTime.class)),
                toOffset(rec.get(NOTIFICATION_EVENT_DEFINITION.UPDATED_AT, LocalDateTime.class))
        );
    }

    private OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
