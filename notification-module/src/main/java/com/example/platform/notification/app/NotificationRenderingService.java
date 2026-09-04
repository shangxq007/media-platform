package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationTemplate.NOTIFICATION_TEMPLATE;

import com.example.platform.notification.domain.NotificationTemplateCode;
import com.example.platform.notification.domain.NotificationTemplatePayload;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationRenderingService {
    private final DSLContext dsl;
    public NotificationRenderingService(DSLContext dsl) { this.dsl = dsl; }

    public NotificationTemplatePayload render(NotificationTemplateCode code, String eventType, String subjectId, Map<String,Object> payload) {
        var rec = dsl.select(NOTIFICATION_TEMPLATE.SUBJECT_TEMPLATE, NOTIFICATION_TEMPLATE.BODY_TEMPLATE)
                .from(NOTIFICATION_TEMPLATE)
                .where(NOTIFICATION_TEMPLATE.TEMPLATE_CODE.eq(code.name()))
                .and(NOTIFICATION_TEMPLATE.CHANNEL.eq("WEBHOOK"))
                .and(NOTIFICATION_TEMPLATE.LOCALE.eq("en"))
                .limit(1)
                .fetchOne();
        String subject = rec != null ? rec.get(NOTIFICATION_TEMPLATE.SUBJECT_TEMPLATE) : eventType;
        String body = rec != null ? rec.get(NOTIFICATION_TEMPLATE.BODY_TEMPLATE) : NotificationPayloadJson.toJson(payload);
        body = body.replace("{{eventType}}", eventType).replace("{{subjectId}}", subjectId).replace("{{payloadJson}}", NotificationPayloadJson.toJson(payload));
        return new NotificationTemplatePayload(subject, body);
    }
}
