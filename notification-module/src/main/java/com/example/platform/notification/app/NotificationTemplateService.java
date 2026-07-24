package com.example.platform.notification.app;

import static com.example.platform.typedschema.jooq.generated.tables.NotificationTemplate.NOTIFICATION_TEMPLATE;

import com.example.platform.notification.domain.NotificationTemplate;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {
    private final DSLContext dsl;
    public NotificationTemplateService(DSLContext dsl) { this.dsl = dsl; }

    public void ensureTemplate(NotificationTemplate template) {
        boolean exists = dsl.fetchExists(
                dsl.selectOne().from(NOTIFICATION_TEMPLATE)
                        .where(NOTIFICATION_TEMPLATE.TEMPLATE_CODE.eq(template.templateCode().name()))
                        .and(NOTIFICATION_TEMPLATE.CHANNEL.eq(template.channel().name()))
                        .and(NOTIFICATION_TEMPLATE.LOCALE.eq(template.locale()))
                        .and(NOTIFICATION_TEMPLATE.VERSION.eq(template.version()))
        );
        if (!exists) {
            dsl.insertInto(NOTIFICATION_TEMPLATE)
                    .columns(NOTIFICATION_TEMPLATE.TEMPLATE_CODE, NOTIFICATION_TEMPLATE.CHANNEL, NOTIFICATION_TEMPLATE.LOCALE, NOTIFICATION_TEMPLATE.VERSION, NOTIFICATION_TEMPLATE.SUBJECT_TEMPLATE, NOTIFICATION_TEMPLATE.BODY_TEMPLATE)
                    .values(template.templateCode().name(), template.channel().name(), template.locale(), template.version(), template.subjectTemplate(), template.bodyTemplate())
                    .execute();
        }
    }
}
