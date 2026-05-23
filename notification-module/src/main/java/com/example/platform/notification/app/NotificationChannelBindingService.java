package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationChannelBinding;
import com.example.platform.notification.infrastructure.WebhookUrlValidator;
import com.example.platform.shared.Ids;
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
public class NotificationChannelBindingService {
    private static final Logger log = LoggerFactory.getLogger(NotificationChannelBindingService.class);

    private static final List<String> SUPPORTED_CHANNELS = List.of("IN_APP", "EMAIL", "SMS", "WEBHOOK", "CHAT", "PUSH");

    private final DSLContext dsl;
    private final AuditPort audit;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final WebhookUrlValidator webhookUrlValidator;

    public NotificationChannelBindingService(DSLContext dsl, AuditPort audit,
            ErrorCodeRegistry errorCodeRegistry, WebhookUrlValidator webhookUrlValidator) {
        this.dsl = dsl;
        this.audit = audit;
        this.errorCodeRegistry = errorCodeRegistry;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    public List<NotificationChannelBinding> listUserBindings(String userId) {
        return dsl.select()
                .from(table("notification_channel_binding"))
                .where(field("user_id").eq(userId))
                .and(field("enabled").eq(true))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public Optional<NotificationChannelBinding> findBinding(String bindingId, String userId) {
        var rec = dsl.select()
                .from(table("notification_channel_binding"))
                .where(field("id").eq(bindingId))
                .and(field("user_id").eq(userId))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    public NotificationChannelBinding createBinding(String userId, String channelType, String destination) {
        if (!SUPPORTED_CHANNELS.contains(channelType)) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_UNSUPPORTED"),
                    "Unsupported channel: " + channelType);
        }

        if ("WEBHOOK".equals(channelType)) {
            webhookUrlValidator.validate(destination,
                    getErrorCode("NOTIFICATION_WEBHOOK_URL_INVALID"),
                    getErrorCode("NOTIFICATION_WEBHOOK_PRIVATE_IP_BLOCKED"));
        }

        String bindingId = Ids.newId("ncb");
        String tenantId = TenantContext.get();
        OffsetDateTime now = OffsetDateTime.now();
        String masked = maskDestination(channelType, destination);

        dsl.insertInto(table("notification_channel_binding"))
                .columns(field("id"), field("tenant_id"), field("user_id"),
                        field("channel_type"), field("destination_masked"),
                        field("destination_encrypted"), field("verified"),
                        field("verification_status"), field("enabled"),
                        field("failure_count"), field("created_at"), field("updated_at"))
                .values(bindingId, tenantId, userId,
                        channelType, masked,
                        destination, false,
                        "PENDING", true,
                        0, now, now)
                .execute();

        audit.record("USER", "NOTIFICATION_CHANNEL_BOUND", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", channelType, "destinationMasked", masked));

        log.info("NotificationChannelBindingService: created binding={} for user={}, channel={}", bindingId, userId, channelType);
        return new NotificationChannelBinding(bindingId, tenantId, null, userId, channelType,
                masked, destination, false, "PENDING", true, null, 0, null, now, now, null);
    }

    public NotificationChannelBinding updateBinding(String bindingId, String userId, String destination) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"),
                        "Channel binding not found: " + bindingId));

        if (destination != null && !destination.isBlank()) {
            if ("WEBHOOK".equals(existing.channelType())) {
                webhookUrlValidator.validate(destination,
                        getErrorCode("NOTIFICATION_WEBHOOK_URL_INVALID"),
                        getErrorCode("NOTIFICATION_WEBHOOK_PRIVATE_IP_BLOCKED"));
            }
            String masked = maskDestination(existing.channelType(), destination);
            dsl.update(table("notification_channel_binding"))
                    .set(field("destination_masked"), masked)
                    .set(field("destination_encrypted"), destination)
                    .set(field("verified"), false)
                    .set(field("verification_status"), "PENDING")
                    .set(field("updated_at"), OffsetDateTime.now())
                    .where(field("id").eq(bindingId))
                    .execute();

            audit.record("USER", "NOTIFICATION_CHANNEL_BOUND", "NOTIFICATION",
                    "CHANNEL_BINDING", bindingId,
                    Map.of("userId", userId, "channelType", existing.channelType(), "action", "updated"));
        }

        return findBinding(bindingId, userId).orElse(existing);
    }

    public NotificationChannelBinding verifyBinding(String bindingId, String userId) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"),
                        "Channel binding not found: " + bindingId));

        dsl.update(table("notification_channel_binding"))
                .set(field("verified"), true)
                .set(field("verification_status"), "VERIFIED")
                .set(field("last_verified_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(bindingId))
                .execute();

        audit.record("USER", "NOTIFICATION_CHANNEL_VERIFIED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType()));

        return new NotificationChannelBinding(existing.bindingId(), existing.tenantId(),
                existing.workspaceId(), existing.userId(), existing.channelType(),
                existing.destinationMasked(), existing.destinationEncrypted(),
                true, "VERIFIED", existing.enabled(), existing.provider(),
                existing.failureCount(), existing.disabledReason(),
                existing.createdAt(), OffsetDateTime.now(), OffsetDateTime.now());
    }

    public NotificationChannelBinding testBinding(String bindingId, String userId) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"),
                        "Channel binding not found: " + bindingId));

        if (!existing.verified()) {
            throw new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_TEST_FAILED"),
                    "Channel must be tested after verification");
        }

        audit.record("USER", "NOTIFICATION_CHANNEL_TESTED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType()));

        return existing;
    }

    public NotificationChannelBinding disableBinding(String bindingId, String userId, String reason) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"),
                        "Channel binding not found: " + bindingId));

        dsl.update(table("notification_channel_binding"))
                .set(field("enabled"), false)
                .set(field("disabled_reason"), reason)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(bindingId))
                .execute();

        audit.record("USER", "NOTIFICATION_CHANNEL_DISABLED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType(), "reason", reason));

        return new NotificationChannelBinding(existing.bindingId(), existing.tenantId(),
                existing.workspaceId(), existing.userId(), existing.channelType(),
                existing.destinationMasked(), existing.destinationEncrypted(),
                existing.verified(), existing.verificationStatus(), false,
                existing.provider(), existing.failureCount(), reason,
                existing.createdAt(), OffsetDateTime.now(), existing.lastVerifiedAt());
    }

    public void deleteBinding(String bindingId, String userId) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(getErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"),
                        "Channel binding not found: " + bindingId));

        dsl.deleteFrom(table("notification_channel_binding"))
                .where(field("id").eq(bindingId))
                .execute();

        audit.record("USER", "NOTIFICATION_CHANNEL_DELETED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType()));
    }

    private String maskDestination(String channelType, String destination) {
        if (destination == null) return null;
        if ("EMAIL".equals(channelType) && destination.contains("@")) {
            int atIdx = destination.indexOf('@');
            if (atIdx > 2) return destination.substring(0, 2) + "***" + destination.substring(atIdx);
            return "***" + destination.substring(atIdx);
        }
        if ("SMS".equals(channelType) && destination.length() > 4) {
            return "***" + destination.substring(destination.length() - 4);
        }
        if (destination.length() > 8) {
            return destination.substring(0, 4) + "***" + destination.substring(destination.length() - 4);
        }
        return "***";
    }

    private NotificationChannelBinding mapRecord(org.jooq.Record rec) {
        return new NotificationChannelBinding(
                rec.get(field("id"), String.class),
                rec.get(field("tenant_id"), String.class),
                rec.get(field("workspace_id"), String.class),
                rec.get(field("user_id"), String.class),
                rec.get(field("channel_type"), String.class),
                rec.get(field("destination_masked"), String.class),
                rec.get(field("destination_encrypted"), String.class),
                Boolean.TRUE.equals(rec.get(field("verified"), Boolean.class)),
                rec.get(field("verification_status"), String.class),
                Boolean.TRUE.equals(rec.get(field("enabled"), Boolean.class)),
                rec.get(field("provider"), String.class),
                rec.get(field("failure_count"), Integer.class) != null ? rec.get(field("failure_count"), Integer.class) : 0,
                rec.get(field("disabled_reason"), String.class),
                rec.get(field("created_at"), OffsetDateTime.class),
                rec.get(field("updated_at"), OffsetDateTime.class),
                rec.get(field("last_verified_at"), OffsetDateTime.class)
        );
    }

    private ConfigurableErrorCode getErrorCode(String code) {
        return errorCodeRegistry.getRequiredErrorCode(code);
    }
}
