package com.example.platform.notification.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.notification.domain.NotificationTemplate;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

@Service
public class NotificationTemplateService {
    private final DSLContext dsl;
    public NotificationTemplateService(DSLContext dsl) { this.dsl = dsl; }

    public void ensureTemplate(NotificationTemplate template) {
        boolean exists = dsl.fetchExists(
                dsl.selectOne().from(table("notification_template"))
                        .where(field("template_code").eq(template.templateCode().name()))
                        .and(field("channel").eq(template.channel().name()))
                        .and(field("locale").eq(template.locale()))
                        .and(field("version").eq(template.version()))
        );
        if (!exists) {
            dsl.insertInto(table("notification_template"))
                    .columns(field("template_code"), field("channel"), field("locale"), field("version"), field("subject_template"), field("body_template"))
                    .values(template.templateCode().name(), template.channel().name(), template.locale(), template.version(), template.subjectTemplate(), template.bodyTemplate())
                    .execute();
        }
    }
}