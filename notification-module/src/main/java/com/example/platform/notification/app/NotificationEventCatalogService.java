package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import java.time.OffsetDateTime;
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
                .from(table("notification_event_definition"))
                .where(field("archived").eq(false))
                .orderBy(field("category"), field("name"))
                .fetch(this::mapRecord);
    }

    public List<NotificationEventDefinition> listUserConfigurableEvents() {
        ensureSeeded();
        return dsl.select()
                .from(table("notification_event_definition"))
                .where(field("archived").eq(false))
                .and(field("user_configurable").eq(true))
                .orderBy(field("category"), field("name"))
                .fetch(this::mapRecord);
    }

    public List<NotificationEventDefinition> listEventsByCategory(String category) {
        ensureSeeded();
        return dsl.select()
                .from(table("notification_event_definition"))
                .where(field("archived").eq(false))
                .and(field("category").eq(category))
                .orderBy(field("name"))
                .fetch(this::mapRecord);
    }

    public Optional<NotificationEventDefinition> findByKey(String eventKey) {
        ensureSeeded();
        var rec = dsl.select()
                .from(table("notification_event_definition"))
                .where(field("event_key").eq(eventKey))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    public NotificationEventDefinition getRequired(String eventKey) {
        return findByKey(eventKey).orElseThrow(() ->
                new IllegalArgumentException("Notification event not found: " + eventKey));
    }

    public NotificationEventDefinition create(NotificationEventDefinition definition) {
        String id = Ids.newId("nevdef");
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("notification_event_definition"))
                .columns(field("id"), field("event_key"), field("name"), field("description"),
                        field("category"), field("severity"), field("visibility"),
                        field("user_configurable"), field("critical"), field("default_enabled"),
                        field("supported_channels"), field("required_permissions"),
                        field("required_entitlements"), field("feature_flag_key"),
                        field("novu_workflow_id"), field("local_template_key"),
                        field("archived"), field("created_at"), field("updated_at"))
                .values(id, definition.eventKey(), definition.name(), definition.description(),
                        definition.category(), definition.severity(), definition.visibility(),
                        definition.userConfigurable(), definition.critical(), definition.defaultEnabled(),
                        Jsons.toJson(definition.supportedChannels()), Jsons.toJson(definition.requiredPermissions()),
                        Jsons.toJson(definition.requiredEntitlements()), definition.featureFlagKey(),
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
                false, now, now);
        eventCache.put(definition.eventKey(), saved);
        return saved;
    }

    public NotificationEventDefinition update(String eventKey, NotificationEventDefinition definition) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.update(table("notification_event_definition"))
                .set(field("name"), definition.name())
                .set(field("description"), definition.description())
                .set(field("category"), definition.category())
                .set(field("severity"), definition.severity())
                .set(field("visibility"), definition.visibility())
                .set(field("user_configurable"), definition.userConfigurable())
                .set(field("critical"), definition.critical())
                .set(field("default_enabled"), definition.defaultEnabled())
                .set(field("supported_channels"), Jsons.toJson(definition.supportedChannels()))
                .set(field("required_permissions"), Jsons.toJson(definition.requiredPermissions()))
                .set(field("required_entitlements"), Jsons.toJson(definition.requiredEntitlements()))
                .set(field("feature_flag_key"), definition.featureFlagKey())
                .set(field("novu_workflow_id"), definition.novuWorkflowId())
                .set(field("local_template_key"), definition.localTemplateKey())
                .set(field("updated_at"), now)
                .where(field("event_key").eq(eventKey))
                .execute();
        NotificationEventDefinition updated = new NotificationEventDefinition(
                definition.eventKey(), definition.name(), definition.description(),
                definition.category(), definition.severity(), definition.visibility(),
                definition.userConfigurable(), definition.critical(), definition.defaultEnabled(),
                definition.supportedChannels(), definition.requiredPermissions(),
                definition.requiredEntitlements(), definition.featureFlagKey(),
                definition.novuWorkflowId(), definition.localTemplateKey(),
                definition.archived(), definition.createdAt(), now);
        eventCache.put(eventKey, updated);
        return updated;
    }

    public void archive(String eventKey) {
        dsl.update(table("notification_event_definition"))
                .set(field("archived"), true)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("event_key").eq(eventKey))
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
                    true, existing.createdAt(), OffsetDateTime.now()));
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
                    dsl.selectOne().from(table("notification_event_definition"))
                            .where(field("event_key").eq(event.eventKey()))
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
        return new NotificationEventDefinition(
                key, name, description, category, severity, visibility,
                userConfigurable, critical, defaultEnabled,
                ALL_CHANNELS, List.of(), List.of(),
                null, null, null, false, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    @SuppressWarnings("unchecked")
    private NotificationEventDefinition mapRecord(org.jooq.Record rec) {
        String supportedChannelsRaw = rec.get(field("supported_channels"), String.class);
        List<String> supportedChannels = supportedChannelsRaw != null && !supportedChannelsRaw.isBlank()
                ? Jsons.fromJson(supportedChannelsRaw, List.class) : ALL_CHANNELS;

        String requiredPermsRaw = rec.get(field("required_permissions"), String.class);
        List<String> requiredPerms = requiredPermsRaw != null && !requiredPermsRaw.isBlank()
                ? Jsons.fromJson(requiredPermsRaw, List.class) : List.of();

        String requiredEntitlementsRaw = rec.get(field("required_entitlements"), String.class);
        List<String> requiredEntitlements = requiredEntitlementsRaw != null && !requiredEntitlementsRaw.isBlank()
                ? Jsons.fromJson(requiredEntitlementsRaw, List.class) : List.of();

        return new NotificationEventDefinition(
                rec.get(field("event_key"), String.class),
                rec.get(field("name"), String.class),
                rec.get(field("description"), String.class),
                rec.get(field("category"), String.class),
                rec.get(field("severity"), String.class),
                rec.get(field("visibility"), String.class),
                Boolean.TRUE.equals(rec.get(field("user_configurable"), Boolean.class)),
                Boolean.TRUE.equals(rec.get(field("critical"), Boolean.class)),
                Boolean.TRUE.equals(rec.get(field("default_enabled"), Boolean.class)),
                supportedChannels,
                requiredPerms,
                requiredEntitlements,
                rec.get(field("feature_flag_key"), String.class),
                rec.get(field("novu_workflow_id"), String.class),
                rec.get(field("local_template_key"), String.class),
                Boolean.TRUE.equals(rec.get(field("archived"), Boolean.class)),
                rec.get(field("created_at"), OffsetDateTime.class),
                rec.get(field("updated_at"), OffsetDateTime.class)
        );
    }
}
