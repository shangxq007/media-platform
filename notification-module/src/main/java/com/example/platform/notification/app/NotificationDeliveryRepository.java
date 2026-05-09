package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.infrastructure.MockNotificationProvider.SentNotification;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDeliveryRepository {

    private final DSLContext dsl;

    public NotificationDeliveryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public String recordDelivery(SentNotification sent) {
        String id = com.example.platform.shared.Ids.newId("ndr");
        dsl.insertInto(table("notification_record"))
                .columns(field("id"), field("event_id"), field("channel"),
                        field("provider_code"), field("status"), field("subject"),
                        field("body"), field("metadata_json"), field("attempt_count"),
                        field("created_at"))
                .values(id, sent.eventId(), sent.channel(),
                        "mock-notification", "SENT", sent.subject(),
                        sent.body(), null, 1,
                        OffsetDateTime.now())
                .execute();
        return id;
    }

    public List<SentNotification> recentDeliveries(int limit) {
        return dsl.select()
                .from(table("notification_record"))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    private SentNotification mapRecord(Record record) {
        return new SentNotification(
                record.get(field("event_id"), String.class),
                record.get(field("channel"), String.class),
                record.get(field("subject"), String.class),
                record.get(field("body"), String.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }
}
