package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationChannelBinding.NOTIFICATION_CHANNEL_BINDING;

import com.example.platform.notification.domain.NotificationChannelBinding;
import com.example.platform.notification.infrastructure.WebhookUrlValidator;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final WebhookUrlValidator webhookUrlValidator;

    public NotificationChannelBindingService(DSLContext dsl, AuditPort audit,
            WebhookUrlValidator webhookUrlValidator) {
        this.dsl = dsl;
        this.audit = audit;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    public List<NotificationChannelBinding> listUserBindings(String userId) {
        return dsl.select()
                .from(NOTIFICATION_CHANNEL_BINDING)
                .where(NOTIFICATION_CHANNEL_BINDING.USER_ID.eq(userId))
                .and(NOTIFICATION_CHANNEL_BINDING.ENABLED.eq(true))
                .orderBy(NOTIFICATION_CHANNEL_BINDING.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public Optional<NotificationChannelBinding> findBinding(String bindingId, String userId) {
        var rec = dsl.select()
                .from(NOTIFICATION_CHANNEL_BINDING)
                .where(NOTIFICATION_CHANNEL_BINDING.ID.eq(bindingId))
                .and(NOTIFICATION_CHANNEL_BINDING.USER_ID.eq(userId))
                .fetchOne();
        return Optional.ofNullable(rec).map(this::mapRecord);
    }

    public NotificationChannelBinding createBinding(String userId, String channelType, String destination) {
        if (!SUPPORTED_CHANNELS.contains(channelType)) {
            throw new PlatformException(NotificationErrorCodes.CHANNEL_UNSUPPORTED,
                    "Unsupported channel: " + channelType);
        }

        if ("WEBHOOK".equals(channelType)) {
            webhookUrlValidator.validate(destination,
                    NotificationErrorCodes.WEBHOOK_URL_INVALID,
                    NotificationErrorCodes.WEBHOOK_PRIVATE_IP_BLOCKED);
        }

        String bindingId = Ids.newId("ncb");
        String tenantId = TenantContext.get();
        LocalDateTime now = LocalDateTime.now();
        String masked = maskDestination(channelType, destination);

        dsl.insertInto(NOTIFICATION_CHANNEL_BINDING)
                .columns(NOTIFICATION_CHANNEL_BINDING.ID, NOTIFICATION_CHANNEL_BINDING.TENANT_ID, NOTIFICATION_CHANNEL_BINDING.USER_ID,
                        NOTIFICATION_CHANNEL_BINDING.CHANNEL_TYPE, NOTIFICATION_CHANNEL_BINDING.DESTINATION_MASKED,
                        NOTIFICATION_CHANNEL_BINDING.DESTINATION_ENCRYPTED, NOTIFICATION_CHANNEL_BINDING.VERIFIED,
                        NOTIFICATION_CHANNEL_BINDING.VERIFICATION_STATUS, NOTIFICATION_CHANNEL_BINDING.ENABLED,
                        NOTIFICATION_CHANNEL_BINDING.FAILURE_COUNT, NOTIFICATION_CHANNEL_BINDING.CREATED_AT, NOTIFICATION_CHANNEL_BINDING.UPDATED_AT)
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
                masked, destination, false, "PENDING", true, null, 0, null, now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC), null);
    }

    public NotificationChannelBinding updateBinding(String bindingId, String userId, String destination) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(NotificationErrorCodes.CHANNEL_NOT_FOUND,
                        "Channel binding not found: " + bindingId));

        if (destination != null && !destination.isBlank()) {
            if ("WEBHOOK".equals(existing.channelType())) {
                webhookUrlValidator.validate(destination,
                        NotificationErrorCodes.WEBHOOK_URL_INVALID,
                        NotificationErrorCodes.WEBHOOK_PRIVATE_IP_BLOCKED);
            }
            String masked = maskDestination(existing.channelType(), destination);
            dsl.update(NOTIFICATION_CHANNEL_BINDING)
                    .set(NOTIFICATION_CHANNEL_BINDING.DESTINATION_MASKED, masked)
                    .set(NOTIFICATION_CHANNEL_BINDING.DESTINATION_ENCRYPTED, destination)
                    .set(NOTIFICATION_CHANNEL_BINDING.VERIFIED, false)
                    .set(NOTIFICATION_CHANNEL_BINDING.VERIFICATION_STATUS, "PENDING")
                    .set(NOTIFICATION_CHANNEL_BINDING.UPDATED_AT, LocalDateTime.now())
                    .where(NOTIFICATION_CHANNEL_BINDING.ID.eq(bindingId))
                    .execute();

            audit.record("USER", "NOTIFICATION_CHANNEL_BOUND", "NOTIFICATION",
                    "CHANNEL_BINDING", bindingId,
                    Map.of("userId", userId, "channelType", existing.channelType(), "action", "updated"));
        }

        return findBinding(bindingId, userId).orElse(existing);
    }

    public NotificationChannelBinding verifyBinding(String bindingId, String userId) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(NotificationErrorCodes.CHANNEL_NOT_FOUND,
                        "Channel binding not found: " + bindingId));

        dsl.update(NOTIFICATION_CHANNEL_BINDING)
                .set(NOTIFICATION_CHANNEL_BINDING.VERIFIED, true)
                .set(NOTIFICATION_CHANNEL_BINDING.VERIFICATION_STATUS, "VERIFIED")
                .set(NOTIFICATION_CHANNEL_BINDING.LAST_VERIFIED_AT, Instant.now())
                .set(NOTIFICATION_CHANNEL_BINDING.UPDATED_AT, LocalDateTime.now())
                .where(NOTIFICATION_CHANNEL_BINDING.ID.eq(bindingId))
                .execute();

        audit.record("USER", "NOTIFICATION_CHANNEL_VERIFIED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType()));

        return new NotificationChannelBinding(existing.bindingId(), existing.tenantId(),
                existing.workspaceId(), existing.userId(), existing.channelType(),
                existing.destinationMasked(), existing.destinationEncrypted(),
                true, "VERIFIED", existing.enabled(), existing.provider(),
                existing.failureCount(), existing.disabledReason(),
                existing.createdAt(), OffsetDateTime.now(), existing.lastVerifiedAt());
    }

    public NotificationChannelBinding testBinding(String bindingId, String userId) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(NotificationErrorCodes.CHANNEL_NOT_FOUND,
                        "Channel binding not found: " + bindingId));

        if (!existing.verified()) {
            throw new PlatformException(NotificationErrorCodes.CHANNEL_TEST_FAILED,
                    "Channel must be tested after verification");
        }

        audit.record("USER", "NOTIFICATION_CHANNEL_TESTED", "NOTIFICATION",
                "CHANNEL_BINDING", bindingId,
                Map.of("userId", userId, "channelType", existing.channelType()));

        return existing;
    }

    public NotificationChannelBinding disableBinding(String bindingId, String userId, String reason) {
        NotificationChannelBinding existing = findBinding(bindingId, userId)
                .orElseThrow(() -> new PlatformException(NotificationErrorCodes.CHANNEL_NOT_FOUND,
                        "Channel binding not found: " + bindingId));

        dsl.update(NOTIFICATION_CHANNEL_BINDING)
                .set(NOTIFICATION_CHANNEL_BINDING.ENABLED, false)
                .set(NOTIFICATION_CHANNEL_BINDING.DISABLED_REASON, reason)
                .set(NOTIFICATION_CHANNEL_BINDING.UPDATED_AT, LocalDateTime.now())
                .where(NOTIFICATION_CHANNEL_BINDING.ID.eq(bindingId))
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
                .orElseThrow(() -> new PlatformException(NotificationErrorCodes.CHANNEL_NOT_FOUND,
                        "Channel binding not found: " + bindingId));

        dsl.deleteFrom(NOTIFICATION_CHANNEL_BINDING)
                .where(NOTIFICATION_CHANNEL_BINDING.ID.eq(bindingId))
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
                rec.get(NOTIFICATION_CHANNEL_BINDING.ID, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.TENANT_ID, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.WORKSPACE_ID, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.USER_ID, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.CHANNEL_TYPE, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.DESTINATION_MASKED, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.DESTINATION_ENCRYPTED, String.class),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_CHANNEL_BINDING.VERIFIED, Boolean.class)),
                rec.get(NOTIFICATION_CHANNEL_BINDING.VERIFICATION_STATUS, String.class),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_CHANNEL_BINDING.ENABLED, Boolean.class)),
                rec.get(NOTIFICATION_CHANNEL_BINDING.PROVIDER, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.FAILURE_COUNT, Integer.class) != null ? rec.get(NOTIFICATION_CHANNEL_BINDING.FAILURE_COUNT, Integer.class) : 0,
                rec.get(NOTIFICATION_CHANNEL_BINDING.DISABLED_REASON, String.class),
                rec.get(NOTIFICATION_CHANNEL_BINDING.CREATED_AT, LocalDateTime.class).atOffset(ZoneOffset.UTC),
                rec.get(NOTIFICATION_CHANNEL_BINDING.UPDATED_AT, LocalDateTime.class).atOffset(ZoneOffset.UTC),
                rec.get(NOTIFICATION_CHANNEL_BINDING.LAST_VERIFIED_AT, Instant.class) != null ? rec.get(NOTIFICATION_CHANNEL_BINDING.LAST_VERIFIED_AT, Instant.class).atOffset(ZoneOffset.UTC) : null
        );
    }

}
