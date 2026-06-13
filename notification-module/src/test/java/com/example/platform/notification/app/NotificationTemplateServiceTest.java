package com.example.platform.notification.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.notification.testsupport.NotificationTestSchemaFixture;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.notification.domain.NotificationTemplate;
import com.example.platform.notification.domain.NotificationTemplateChannel;
import com.example.platform.notification.domain.NotificationTemplateCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationTemplateServiceTest extends PostgresTestContainerSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private NotificationTemplateService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        NotificationTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        NotificationTestSchemaFixture.truncate(dsl);
        service = new NotificationTemplateService(dsl);
    }

    @Test
    void ensureTemplateInsertsWhenNotExists() {
        NotificationTemplate template = new NotificationTemplate(
                null, NotificationTemplateCode.RENDER_CREATED,
                NotificationTemplateChannel.WEBHOOK, "en", 1,
                "Render created", "{\"eventType\":\"{{eventType}}\"}");

        service.ensureTemplate(template);

        List<Map<String, Object>> rows = dsl.select()
                .from(table("notification_template"))
                .where(field("template_code").eq("RENDER_CREATED"))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("WEBHOOK", rows.get(0).get("channel"));
        assertEquals("en", rows.get(0).get("locale"));
        assertEquals(1, rows.get(0).get("version"));
    }

    @Test
    void ensureTemplateDoesNotDuplicate() {
        NotificationTemplate template = new NotificationTemplate(
                null, NotificationTemplateCode.RENDER_FINISHED,
                NotificationTemplateChannel.EMAIL, "en", 1,
                "Render finished", "body");

        service.ensureTemplate(template);
        service.ensureTemplate(template);

        List<Map<String, Object>> rows = dsl.select()
                .from(table("notification_template"))
                .where(field("template_code").eq("RENDER_FINISHED"))
                .fetchMaps();

        assertEquals(1, rows.size(), "Should not insert duplicate template");
    }

    @Test
    void ensureTemplateAllowsDifferentChannels() {
        NotificationTemplate webhook = new NotificationTemplate(
                null, NotificationTemplateCode.GENERIC_EVENT,
                NotificationTemplateChannel.WEBHOOK, "en", 1,
                "subject", "body");
        NotificationTemplate email = new NotificationTemplate(
                null, NotificationTemplateCode.GENERIC_EVENT,
                NotificationTemplateChannel.EMAIL, "en", 1,
                "subject", "body");

        service.ensureTemplate(webhook);
        service.ensureTemplate(email);

        List<Map<String, Object>> rows = dsl.select()
                .from(table("notification_template"))
                .where(field("template_code").eq("GENERIC_EVENT"))
                .fetchMaps();

        assertEquals(2, rows.size(), "Different channels should be separate rows");
    }

    @Test
    void ensureTemplateAllowsDifferentVersions() {
        NotificationTemplate v1 = new NotificationTemplate(
                null, NotificationTemplateCode.RENDER_CREATED,
                NotificationTemplateChannel.WEBHOOK, "en", 1,
                "subject", "body v1");
        NotificationTemplate v2 = new NotificationTemplate(
                null, NotificationTemplateCode.RENDER_CREATED,
                NotificationTemplateChannel.WEBHOOK, "en", 2,
                "subject", "body v2");

        service.ensureTemplate(v1);
        service.ensureTemplate(v2);

        List<Map<String, Object>> rows = dsl.select()
                .from(table("notification_template"))
                .where(field("template_code").eq("RENDER_CREATED"))
                .fetchMaps();

        assertEquals(2, rows.size(), "Different versions should be separate rows");
    }
}
