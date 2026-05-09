package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationTemplateCode;
import com.example.platform.notification.domain.NotificationTemplatePayload;
import com.example.platform.shared.Jsons;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationRenderingService {
    private final DSLContext dsl;
    public NotificationRenderingService(DSLContext dsl) { this.dsl = dsl; }

    public NotificationTemplatePayload render(NotificationTemplateCode code, String eventType, String subjectId, Map<String,Object> payload) {
        var rec = dsl.select(field("subject_template", String.class), field("body_template", String.class))
                .from(table("notification_template"))
                .where(field("template_code").eq(code.name()))
                .and(field("channel").eq("WEBHOOK"))
                .and(field("locale").eq("en"))
                .limit(1)
                .fetchOne();
        String subject = rec != null ? rec.get(field("subject_template", String.class)) : eventType;
        String body = rec != null ? rec.get(field("body_template", String.class)) : Jsons.toJson(payload);
        body = body.replace("{{eventType}}", eventType).replace("{{subjectId}}", subjectId).replace("{{payloadJson}}", Jsons.toJson(payload));
        return new NotificationTemplatePayload(subject, body);
    }
}