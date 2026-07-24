package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationPreference.NOTIFICATION_PREFERENCE;

import com.example.platform.notification.domain.NotificationPreference;
import com.example.platform.shared.Ids;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.TenantContext;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;
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
        var rec = dsl.selectFrom(NOTIFICATION_PREFERENCE)
                .where(NOTIFICATION_PREFERENCE.USER_ID.eq(userId))
                .fetchOne();
        if (rec != null) return mapRecord(rec);
        return createDefaultPreferences(userId);
    }

    public NotificationPreference updatePreferences(String userId, boolean globalEnabled,
            Map<String, Boolean> channelEnabled, Map<String, Boolean> eventEnabled,
            String quietHoursStart, String quietHoursEnd, String quietHoursTimezone,
            String digestMode, boolean criticalOverride) {
        String tenantId = TenantContext.get();
        String preferenceId = Ids.newId("npr");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(NOTIFICATION_PREFERENCE)
                .set(NOTIFICATION_PREFERENCE.ID, preferenceId)
                .set(NOTIFICATION_PREFERENCE.TENANT_ID, tenantId)
                .set(NOTIFICATION_PREFERENCE.USER_ID, userId)
                .set(NOTIFICATION_PREFERENCE.GLOBAL_ENABLED, globalEnabled)
                .set(NOTIFICATION_PREFERENCE.CHANNEL_ENABLED, Jsons.toJson(channelEnabled != null ? channelEnabled : Map.of()))
                .set(NOTIFICATION_PREFERENCE.EVENT_ENABLED, Jsons.toJson(eventEnabled != null ? eventEnabled : Map.of()))
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_START, quietHoursStart)
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_END, quietHoursEnd)
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_TIMEZONE, quietHoursTimezone)
                .set(NOTIFICATION_PREFERENCE.DIGEST_MODE, digestMode != null ? digestMode : "IMMEDIATE")
                .set(NOTIFICATION_PREFERENCE.CRITICAL_OVERRIDE, criticalOverride)
                .set(NOTIFICATION_PREFERENCE.CREATED_AT, now)
                .set(NOTIFICATION_PREFERENCE.UPDATED_AT, now)
                .onConflict(NOTIFICATION_PREFERENCE.TENANT_ID, NOTIFICATION_PREFERENCE.USER_ID)
                .doUpdate()
                .set(NOTIFICATION_PREFERENCE.GLOBAL_ENABLED, globalEnabled)
                .set(NOTIFICATION_PREFERENCE.CHANNEL_ENABLED, Jsons.toJson(channelEnabled != null ? channelEnabled : Map.of()))
                .set(NOTIFICATION_PREFERENCE.EVENT_ENABLED, Jsons.toJson(eventEnabled != null ? eventEnabled : Map.of()))
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_START, quietHoursStart)
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_END, quietHoursEnd)
                .set(NOTIFICATION_PREFERENCE.QUIET_HOURS_TIMEZONE, quietHoursTimezone)
                .set(NOTIFICATION_PREFERENCE.DIGEST_MODE, digestMode != null ? digestMode : "IMMEDIATE")
                .set(NOTIFICATION_PREFERENCE.CRITICAL_OVERRIDE, criticalOverride)
                .set(NOTIFICATION_PREFERENCE.UPDATED_AT, now)
                .execute();

        audit.record("USER", "NOTIFICATION_PREFERENCE_UPDATED", "NOTIFICATION",
                "PREFERENCE", userId,
                Map.of("userId", userId, "globalEnabled", globalEnabled,
                        "digestMode", digestMode, "criticalOverride", criticalOverride));

        log.info("NotificationPreferenceService: updated preferences for user={}", userId);
        OffsetDateTime nowOdt = now.atOffset(ZoneOffset.UTC);
        return new NotificationPreference(preferenceId,
                tenantId, userId, globalEnabled, channelEnabled, eventEnabled,
                quietHoursStart, quietHoursEnd, quietHoursTimezone,
                digestMode != null ? digestMode : "IMMEDIATE", criticalOverride,
                nowOdt, nowOdt);
    }

    private NotificationPreference createDefaultPreferences(String userId) {
        String preferenceId = Ids.newId("npr");
        String tenantId = TenantContext.get();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Boolean> defaultChannels = Map.of("IN_APP", true, "EMAIL", true, "SMS", false, "WEBHOOK", false);

        dsl.insertInto(NOTIFICATION_PREFERENCE)
                .set(NOTIFICATION_PREFERENCE.ID, preferenceId)
                .set(NOTIFICATION_PREFERENCE.TENANT_ID, tenantId)
                .set(NOTIFICATION_PREFERENCE.USER_ID, userId)
                .set(NOTIFICATION_PREFERENCE.GLOBAL_ENABLED, true)
                .set(NOTIFICATION_PREFERENCE.CHANNEL_ENABLED, Jsons.toJson(defaultChannels))
                .set(NOTIFICATION_PREFERENCE.EVENT_ENABLED, Jsons.toJson(Map.of()))
                .set(NOTIFICATION_PREFERENCE.DIGEST_MODE, "IMMEDIATE")
                .set(NOTIFICATION_PREFERENCE.CRITICAL_OVERRIDE, true)
                .set(NOTIFICATION_PREFERENCE.CREATED_AT, now)
                .set(NOTIFICATION_PREFERENCE.UPDATED_AT, now)
                .onConflict(NOTIFICATION_PREFERENCE.TENANT_ID, NOTIFICATION_PREFERENCE.USER_ID)
                .doNothing()
                .execute();

        OffsetDateTime nowOdt = now.atOffset(ZoneOffset.UTC);
        return new NotificationPreference(preferenceId, tenantId, userId,
                true, defaultChannels, Map.of(), null, null, null,
                "IMMEDIATE", true, nowOdt, nowOdt);
    }

    @SuppressWarnings("unchecked")
    private NotificationPreference mapRecord(Record rec) {
        String channelEnabledRaw = rec.get(NOTIFICATION_PREFERENCE.CHANNEL_ENABLED);
        Map<String, Boolean> channelEnabled = channelEnabledRaw != null && !channelEnabledRaw.isBlank()
                ? Jsons.fromJson(channelEnabledRaw, Map.class) : Map.of();

        String eventEnabledRaw = rec.get(NOTIFICATION_PREFERENCE.EVENT_ENABLED);
        Map<String, Boolean> eventEnabled = eventEnabledRaw != null && !eventEnabledRaw.isBlank()
                ? Jsons.fromJson(eventEnabledRaw, Map.class) : Map.of();

        LocalDateTime createdAtLdt = rec.get(NOTIFICATION_PREFERENCE.CREATED_AT);
        LocalDateTime updatedAtLdt = rec.get(NOTIFICATION_PREFERENCE.UPDATED_AT);

        return new NotificationPreference(
                rec.get(NOTIFICATION_PREFERENCE.ID),
                rec.get(NOTIFICATION_PREFERENCE.TENANT_ID),
                rec.get(NOTIFICATION_PREFERENCE.USER_ID),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_PREFERENCE.GLOBAL_ENABLED)),
                channelEnabled,
                eventEnabled,
                rec.get(NOTIFICATION_PREFERENCE.QUIET_HOURS_START),
                rec.get(NOTIFICATION_PREFERENCE.QUIET_HOURS_END),
                rec.get(NOTIFICATION_PREFERENCE.QUIET_HOURS_TIMEZONE),
                rec.get(NOTIFICATION_PREFERENCE.DIGEST_MODE),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_PREFERENCE.CRITICAL_OVERRIDE)),
                createdAtLdt != null ? createdAtLdt.atOffset(ZoneOffset.UTC) : null,
                updatedAtLdt != null ? updatedAtLdt.atOffset(ZoneOffset.UTC) : null
        );
    }
}
