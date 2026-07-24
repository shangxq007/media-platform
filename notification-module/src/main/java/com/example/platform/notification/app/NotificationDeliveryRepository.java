package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationRecord.NOTIFICATION_RECORD;

import com.example.platform.notification.infrastructure.MockNotificationProvider.SentNotification;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        dsl.insertInto(NOTIFICATION_RECORD)
                .columns(NOTIFICATION_RECORD.ID, NOTIFICATION_RECORD.EVENT_ID, NOTIFICATION_RECORD.CHANNEL,
                        NOTIFICATION_RECORD.PROVIDER_CODE, NOTIFICATION_RECORD.STATUS, NOTIFICATION_RECORD.SUBJECT,
                        NOTIFICATION_RECORD.BODY, NOTIFICATION_RECORD.METADATA_JSON, NOTIFICATION_RECORD.ATTEMPT_COUNT,
                        NOTIFICATION_RECORD.CREATED_AT)
                .values(id, sent.eventId(), sent.channel(),
                        "mock-notification", "SENT", sent.subject(),
                        sent.body(), null, 1,
                        LocalDateTime.now(ZoneOffset.UTC))
                .execute();
        return id;
    }

    public List<SentNotification> recentDeliveries(int limit) {
        return dsl.select()
                .from(NOTIFICATION_RECORD)
                .orderBy(NOTIFICATION_RECORD.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    private SentNotification mapRecord(Record record) {
        return new SentNotification(
                record.get(NOTIFICATION_RECORD.EVENT_ID),
                record.get(NOTIFICATION_RECORD.CHANNEL),
                record.get(NOTIFICATION_RECORD.SUBJECT),
                record.get(NOTIFICATION_RECORD.BODY),
                record.get(NOTIFICATION_RECORD.CREATED_AT).toInstant(ZoneOffset.UTC)
        );
    }
}
