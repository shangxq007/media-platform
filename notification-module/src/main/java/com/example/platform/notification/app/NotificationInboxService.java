package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationInboxItem;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        OffsetDateTime now = OffsetDateTime.now();

        dsl.insertInto(table("notification_user_inbox"))
                .columns(field("id"), field("tenant_id"), field("user_id"),
                        field("event_key"), field("type"), field("title"),
                        field("message"), field("read"), field("link"),
                        field("actor_id"), field("resource_type"), field("resource_id"),
                        field("created_at"))
                .values(id, tenantId, userId,
                        eventKey, type != null ? type : "INFO", title,
                        message, false, link,
                        actorId, resourceType, resourceId,
                        now)
                .execute();

        log.info("NotificationInboxService: created inbox item={} for user={}, event={}", id, userId, eventKey);
        return new NotificationInboxItem(id, tenantId, null, userId, eventKey,
                type, title, message, false, link, actorId, resourceType, resourceId, now, null);
    }

    public List<NotificationInboxItem> listUserInbox(String userId, int limit) {
        return dsl.select()
                .from(table("notification_user_inbox"))
                .where(field("user_id").eq(userId))
                .orderBy(field("created_at").desc())
                .limit(limit > 0 ? limit : 50)
                .fetch(this::mapRecord);
    }

    public List<NotificationInboxItem> listUnread(String userId, int limit) {
        return dsl.select()
                .from(table("notification_user_inbox"))
                .where(field("user_id").eq(userId))
                .and(field("read").eq(false))
                .orderBy(field("created_at").desc())
                .limit(limit > 0 ? limit : 50)
                .fetch(this::mapRecord);
    }

    public long getUnreadCount(String userId) {
        return dsl.selectCount()
                .from(table("notification_user_inbox"))
                .where(field("user_id").eq(userId))
                .and(field("read").eq(false))
                .fetchOne(0, Long.class);
    }

    public Optional<NotificationInboxItem> markAsRead(String id, String userId) {
        var rec = dsl.select()
                .from(table("notification_user_inbox"))
                .where(field("id").eq(id))
                .and(field("user_id").eq(userId))
                .fetchOne();

        if (rec == null) return Optional.empty();

        boolean alreadyRead = Boolean.TRUE.equals(rec.get(field("read"), Boolean.class));
        if (!alreadyRead) {
            dsl.update(table("notification_user_inbox"))
                    .set(field("read"), true)
                    .set(field("read_at"), OffsetDateTime.now())
                    .where(field("id").eq(id))
                    .execute();
        }

        NotificationInboxItem item = mapRecord(rec);
        return Optional.of(new NotificationInboxItem(
                item.id(), item.tenantId(), item.workspaceId(), item.userId(),
                item.eventKey(), item.type(), item.title(), item.message(),
                true, item.link(), item.actorId(), item.resourceType(), item.resourceId(),
                item.createdAt(), alreadyRead ? item.readAt() : OffsetDateTime.now()));
    }

    public void markAllAsRead(String userId) {
        dsl.update(table("notification_user_inbox"))
                .set(field("read"), true)
                .set(field("read_at"), OffsetDateTime.now())
                .where(field("user_id").eq(userId))
                .and(field("read").eq(false))
                .execute();
    }

    private NotificationInboxItem mapRecord(org.jooq.Record rec) {
        return new NotificationInboxItem(
                rec.get(field("id"), String.class),
                rec.get(field("tenant_id"), String.class),
                rec.get(field("workspace_id"), String.class),
                rec.get(field("user_id"), String.class),
                rec.get(field("event_key"), String.class),
                rec.get(field("type"), String.class),
                rec.get(field("title"), String.class),
                rec.get(field("message"), String.class),
                Boolean.TRUE.equals(rec.get(field("read"), Boolean.class)),
                rec.get(field("link"), String.class),
                rec.get(field("actor_id"), String.class),
                rec.get(field("resource_type"), String.class),
                rec.get(field("resource_id"), String.class),
                rec.get(field("created_at"), OffsetDateTime.class),
                rec.get(field("read_at"), OffsetDateTime.class)
        );
    }
}
