package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationPreference;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationPreferenceService {
    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceService.class);

    private final DSLContext dsl;
    private final AuditPort audit;

    public NotificationPreferenceService(DSLContext dsl, AuditPort audit) {
        this.dsl = dsl;
        this.audit = audit;
    }

    public NotificationPreference getPreferences(String userId) {
        var rec = dsl.select()
                .from(table("notification_preference"))
                .where(field("user_id").eq(userId))
                .fetchOne();
        if (rec != null) return mapRecord(rec);
        return createDefaultPreferences(userId);
    }

    public NotificationPreference updatePreferences(String userId, boolean globalEnabled,
            Map<String, Boolean> channelEnabled, Map<String, Boolean> eventEnabled,
            String quietHoursStart, String quietHoursEnd, String quietHoursTimezone,
            String digestMode, boolean criticalOverride) {
        String tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();

        Optional<NotificationPreference> existing = findPreference(userId);
        if (existing.isPresent()) {
            dsl.update(table("notification_preference"))
                    .set(field("global_enabled"), globalEnabled)
                    .set(field("channel_enabled"), Jsons.toJson(channelEnabled != null ? channelEnabled : Map.of()))
                    .set(field("event_enabled"), Jsons.toJson(eventEnabled != null ? eventEnabled : Map.of()))
                    .set(field("quiet_hours_start"), quietHoursStart)
                    .set(field("quiet_hours_end"), quietHoursEnd)
                    .set(field("quiet_hours_timezone"), quietHoursTimezone)
                    .set(field("digest_mode"), digestMode != null ? digestMode : "IMMEDIATE")
                    .set(field("critical_override"), criticalOverride)
                    .set(field("updated_at"), now)
                    .where(field("user_id").eq(userId))
                    .execute();
        } else {
            String preferenceId = Ids.newId("npr");
            dsl.insertInto(table("notification_preference"))
                    .columns(field("id"), field("tenant_id"), field("user_id"),
                            field("global_enabled"), field("channel_enabled"), field("event_enabled"),
                            field("quiet_hours_start"), field("quiet_hours_end"),
                            field("quiet_hours_timezone"), field("digest_mode"),
                            field("critical_override"), field("created_at"), field("updated_at"))
                    .values(preferenceId, tenantId, userId,
                            globalEnabled,
                            Jsons.toJson(channelEnabled != null ? channelEnabled : Map.of()),
                            Jsons.toJson(eventEnabled != null ? eventEnabled : Map.of()),
                            quietHoursStart, quietHoursEnd,
                            quietHoursTimezone,
                            digestMode != null ? digestMode : "IMMEDIATE",
                            criticalOverride, now, now)
                    .execute();
        }

        audit.record("USER", "NOTIFICATION_PREFERENCE_UPDATED", "NOTIFICATION",
                "PREFERENCE", userId,
                Map.of("userId", userId, "globalEnabled", globalEnabled,
                        "digestMode", digestMode, "criticalOverride", criticalOverride));

        log.info("NotificationPreferenceService: updated preferences for user={}", userId);
        return new NotificationPreference(existing.map(NotificationPreference::preferenceId).orElse(Ids.newId("npr")),
                tenantId, null, userId, globalEnabled, channelEnabled, eventEnabled,
                quietHoursStart, quietHoursEnd, quietHoursTimezone,
                digestMode != null ? digestMode : "IMMEDIATE", criticalOverride,
                existing.map(NotificationPreference::createdAt).orElse(now), now);
    }

    private NotificationPreference createDefaultPreferences(String userId) {
        String preferenceId = Ids.newId("npr");
        String tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Boolean> defaultChannels = Map.of("IN_APP", true, "EMAIL", true, "SMS", false, "WEBHOOK", false);

        dsl.insertInto(table("notification_preference"))
                .columns(field("id"), field("tenant_id"), field("user_id"),
                        field("global_enabled"), field("channel_enabled"), field("event_enabled"),
                        field("digest_mode"), field("critical_override"),
                        field("created_at"), field("updated_at"))
                .values(preferenceId, tenantId, userId,
                        true, Jsons.toJson(defaultChannels), Jsons.toJson(Map.of()),
                        "IMMEDIATE", true, now, now)
                .execute();

        return new NotificationPreference(preferenceId, tenantId, null, userId,
                true, defaultChannels, Map.of(), null, null, null,
                "IMMEDIATE", true, now, now);
    }

    private Optional<NotificationPreference> findPreference(String userId) {
        var rec = dsl.select()
                .from(table("notification_preference"))
                .where(field("user_id").eq(userId))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    @SuppressWarnings("unchecked")
    private NotificationPreference mapRecord(org.jooq.Record rec) {
        String channelEnabledRaw = rec.get(field("channel_enabled"), String.class);
        Map<String, Boolean> channelEnabled = channelEnabledRaw != null && !channelEnabledRaw.isBlank()
                ? Jsons.fromJson(channelEnabledRaw, Map.class) : Map.of();

        String eventEnabledRaw = rec.get(field("event_enabled"), String.class);
        Map<String, Boolean> eventEnabled = eventEnabledRaw != null && !eventEnabledRaw.isBlank()
                ? Jsons.fromJson(eventEnabledRaw, Map.class) : Map.of();

        return new NotificationPreference(
                rec.get(field("id"), String.class),
                rec.get(field("tenant_id"), String.class),
                rec.get(field("workspace_id"), String.class),
                rec.get(field("user_id"), String.class),
                Boolean.TRUE.equals(rec.get(field("global_enabled"), Boolean.class)),
                channelEnabled,
                eventEnabled,
                rec.get(field("quiet_hours_start"), String.class),
                rec.get(field("quiet_hours_end"), String.class),
                rec.get(field("quiet_hours_timezone"), String.class),
                rec.get(field("digest_mode"), String.class),
                Boolean.TRUE.equals(rec.get(field("critical_override"), Boolean.class)),
                rec.get(field("created_at"), OffsetDateTime.class),
                rec.get(field("updated_at"), OffsetDateTime.class)
        );
    }
}
