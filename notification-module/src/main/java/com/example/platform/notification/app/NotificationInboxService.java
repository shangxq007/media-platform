package com.example.platform.notification.app;

import com.example.platform.notification.domain.NotificationInboxItem;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.NotificationUserInbox.NOTIFICATION_USER_INBOX;


@Service
public class NotificationInboxService {
    private static final Logger log = LoggerFactory.getLogger(NotificationInboxService.class);

    private final DSLContext dsl;

    public NotificationInboxService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public NotificationInboxItem createInboxItem(String userId, String eventKey, String type,
            String title, String message, String link, String actorId,
            String resourceType, String resourceId) {
        String id = Ids.newId("ninb");
        String tenantId = TenantContext.get();
        LocalDateTime now = LocalDateTime.now();

        dsl.insertInto(NOTIFICATION_USER_INBOX)
                .columns(NOTIFICATION_USER_INBOX.ID, NOTIFICATION_USER_INBOX.TENANT_ID, NOTIFICATION_USER_INBOX.USER_ID,
                        NOTIFICATION_USER_INBOX.EVENT_KEY, NOTIFICATION_USER_INBOX.TYPE, NOTIFICATION_USER_INBOX.TITLE,
                        NOTIFICATION_USER_INBOX.MESSAGE, NOTIFICATION_USER_INBOX.READ, NOTIFICATION_USER_INBOX.LINK,
                        NOTIFICATION_USER_INBOX.ACTOR_ID, NOTIFICATION_USER_INBOX.RESOURCE_TYPE, NOTIFICATION_USER_INBOX.RESOURCE_ID,
                        NOTIFICATION_USER_INBOX.CREATED_AT)
                .values(id, tenantId, userId,
                        eventKey, type != null ? type : "INFO", title,
                        message, false, link,
                        actorId, resourceType, resourceId,
                        now)
                .execute();

        log.info("NotificationInboxService: created inbox item={} for user={}, event={}", id, userId, eventKey);
        return new NotificationInboxItem(id, tenantId, null, userId, eventKey,
                type, title, message, false, link, actorId, resourceType, resourceId, now.atOffset(ZoneOffset.UTC), null);
    }

    public List<NotificationInboxItem> listUserInbox(String userId, int limit) {
        return dsl.select()
                .from(NOTIFICATION_USER_INBOX)
                .where(NOTIFICATION_USER_INBOX.USER_ID.eq(userId))
                .orderBy(NOTIFICATION_USER_INBOX.CREATED_AT.desc())
                .limit(limit > 0 ? limit : 50)
                .fetch(this::mapRecord);
    }

    public List<NotificationInboxItem> listUnread(String userId, int limit) {
        return dsl.select()
                .from(NOTIFICATION_USER_INBOX)
                .where(NOTIFICATION_USER_INBOX.USER_ID.eq(userId))
                .and(NOTIFICATION_USER_INBOX.READ.eq(false))
                .orderBy(NOTIFICATION_USER_INBOX.CREATED_AT.desc())
                .limit(limit > 0 ? limit : 50)
                .fetch(this::mapRecord);
    }

    public long getUnreadCount(String userId) {
        return dsl.selectCount()
                .from(NOTIFICATION_USER_INBOX)
                .where(NOTIFICATION_USER_INBOX.USER_ID.eq(userId))
                .and(NOTIFICATION_USER_INBOX.READ.eq(false))
                .fetchOne(0, Long.class);
    }

    public Optional<NotificationInboxItem> markAsRead(String id, String userId) {
        var rec = dsl.select()
                .from(NOTIFICATION_USER_INBOX)
                .where(NOTIFICATION_USER_INBOX.ID.eq(id))
                .and(NOTIFICATION_USER_INBOX.USER_ID.eq(userId))
                .fetchOne();

        if (rec == null) return Optional.empty();

        boolean alreadyRead = Boolean.TRUE.equals(rec.get(NOTIFICATION_USER_INBOX.READ, Boolean.class));
        if (!alreadyRead) {
            dsl.update(NOTIFICATION_USER_INBOX)
                    .set(NOTIFICATION_USER_INBOX.READ, true)
                    .set(NOTIFICATION_USER_INBOX.READ_AT, LocalDateTime.now())
                    .where(NOTIFICATION_USER_INBOX.ID.eq(id))
                    .execute();
        }

        NotificationInboxItem item = mapRecord(rec);
        return Optional.of(new NotificationInboxItem(
                item.id(), item.tenantId(), item.workspaceId(), item.userId(),
                item.eventKey(), item.type(), item.title(), item.message(),
                true, item.link(), item.actorId(), item.resourceType(), item.resourceId(),
                item.createdAt(), alreadyRead ? item.readAt() : LocalDateTime.now().atOffset(ZoneOffset.UTC)));
    }

    public void markAllAsRead(String userId) {
        dsl.update(NOTIFICATION_USER_INBOX)
                .set(NOTIFICATION_USER_INBOX.READ, true)
                .set(NOTIFICATION_USER_INBOX.READ_AT, LocalDateTime.now())
                .where(NOTIFICATION_USER_INBOX.USER_ID.eq(userId))
                .and(NOTIFICATION_USER_INBOX.READ.eq(false))
                .execute();
    }

    private NotificationInboxItem mapRecord(org.jooq.Record rec) {
        return new NotificationInboxItem(
                rec.get(NOTIFICATION_USER_INBOX.ID, String.class),
                rec.get(NOTIFICATION_USER_INBOX.TENANT_ID, String.class),
                rec.get(NOTIFICATION_USER_INBOX.WORKSPACE_ID, String.class),
                rec.get(NOTIFICATION_USER_INBOX.USER_ID, String.class),
                rec.get(NOTIFICATION_USER_INBOX.EVENT_KEY, String.class),
                rec.get(NOTIFICATION_USER_INBOX.TYPE, String.class),
                rec.get(NOTIFICATION_USER_INBOX.TITLE, String.class),
                rec.get(NOTIFICATION_USER_INBOX.MESSAGE, String.class),
                Boolean.TRUE.equals(rec.get(NOTIFICATION_USER_INBOX.READ, Boolean.class)),
                rec.get(NOTIFICATION_USER_INBOX.LINK, String.class),
                rec.get(NOTIFICATION_USER_INBOX.ACTOR_ID, String.class),
                rec.get(NOTIFICATION_USER_INBOX.RESOURCE_TYPE, String.class),
                rec.get(NOTIFICATION_USER_INBOX.RESOURCE_ID, String.class),
                toOffset(rec.get(NOTIFICATION_USER_INBOX.CREATED_AT, LocalDateTime.class)),
                toOffset(rec.get(NOTIFICATION_USER_INBOX.READ_AT, LocalDateTime.class))
        );
    }

    private OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt != null ? ldt.atOffset(ZoneOffset.UTC) : null;
    }
}
