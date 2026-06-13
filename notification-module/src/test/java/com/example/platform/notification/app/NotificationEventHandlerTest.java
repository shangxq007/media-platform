package com.example.platform.notification.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import com.example.platform.notification.domain.NotificationInboundEvent;
import com.example.platform.notification.domain.NotificationProvider;
import com.example.platform.notification.domain.NotificationTemplateCode;
import com.example.platform.notification.domain.NotificationTemplatePayload;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationEventHandlerTest extends PostgresTestContainerSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private Connection conn;
    private NotificationEventHandler handler;
    private NotificationRenderingService renderingService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        // Create tables
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        // Tables will be created inline
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clean tables
        dsl.execute("TRUNCATE TABLE notification_event, notification_template, notification_delivery, notification_record, notification_subscription, notification_channel_binding RESTART IDENTITY CASCADE");
        String dbName = "eventhandlertest" + COUNTER.incrementAndGet();
        // Using shared PostgreSQL connection
        // Using shared dsl

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table notification_event ("
                    + "id varchar(64) primary key,"
                    + "event_type varchar(100) not null,"
                    + "subject_id varchar(100) not null,"
                    + "payload text,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table notification_delivery ("
                    + "id varchar(64) primary key,"
                    + "event_id varchar(64) not null,"
                    + "channel varchar(20) not null,"
                    + "provider_code varchar(50) not null,"
                    + "status varchar(20) not null,"
                    + "request_payload text,"
                    + "response_payload text,"
                    + "attempt_count int not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table notification_template ("
                    + "template_code varchar(64) not null,"
                    + "channel varchar(20) not null,"
                    + "locale varchar(10) not null,"
                    + "version int not null,"
                    + "subject_template varchar(500),"
                    + "body_template text"
                    + ")");
        }

        renderingService = mock(NotificationRenderingService.class);
        when(renderingService.render(
                org.mockito.ArgumentMatchers.any(NotificationTemplateCode.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap()
        )).thenReturn(new NotificationTemplatePayload("test-subject", "test-body"));

        NotificationProvider emailProvider = new NotificationProvider() {
            @Override public String channel() { return "EMAIL"; }
            @Override public String providerCode() { return "stub-email"; }
            @Override public DeliveryResult send(DeliveryCommand command) {
                return new DeliveryResult("SENT", "{\"channel\":\"EMAIL\"}");
            }
        };

        NotificationProvider webhookProvider = new NotificationProvider() {
            @Override public String channel() { return "WEBHOOK"; }
            @Override public String providerCode() { return "local-webhook"; }
            @Override public DeliveryResult send(DeliveryCommand command) {
                return new DeliveryResult("SENT", "{\"channel\":\"WEBHOOK\"}");
            }
        };

        handler = new NotificationEventHandler(
                dsl, List.of(emailProvider, webhookProvider), renderingService);
    }

    @Test
    void handlePersistsEventAndCreatesDeliveries() {
        NotificationInboundEvent event = new NotificationInboundEvent(
                "render.job.created", "rj-123",
                Map.of("renderJobId", "rj-123", "projectId", "proj-1"));

        handler.handle(event);

        List<Map<String, Object>> events = dsl.select()
                .from(table("notification_event"))
                .fetchMaps();
        assertEquals(1, events.size());
        assertEquals("render.job.created", events.get(0).get("event_type"));
        assertEquals("rj-123", events.get(0).get("subject_id"));

        List<Map<String, Object>> deliveries = dsl.select()
                .from(table("notification_delivery"))
                .fetchMaps();
        assertEquals(2, deliveries.size(), "Should create one delivery per provider");
    }

    @Test
    void handleRoutesToCorrectProviders() {
        NotificationInboundEvent event = new NotificationInboundEvent(
                "render.job.finished", "rj-456",
                Map.of("renderJobId", "rj-456"));

        handler.handle(event);

        List<Map<String, Object>> deliveries = dsl.select()
                .from(table("notification_delivery"))
                .orderBy(field("channel"))
                .fetchMaps();

        assertEquals(2, deliveries.size());
        assertEquals("EMAIL", deliveries.get(0).get("channel"));
        assertEquals("stub-email", deliveries.get(0).get("provider_code"));
        assertEquals("WEBHOOK", deliveries.get(1).get("channel"));
        assertEquals("local-webhook", deliveries.get(1).get("provider_code"));
    }

    @Test
    void onRenderJobCreatedConvertsEventAndHandles() {
        RenderJobCreatedEvent event = new RenderJobCreatedEvent(
                "rj-789", "proj-1", "snap-1", "social_1080p", "ffmpeg");

        handler.onRenderJobCreated(event);

        List<Map<String, Object>> events = dsl.select()
                .from(table("notification_event"))
                .fetchMaps();
        assertEquals(1, events.size());
        assertEquals("render.job.created", events.get(0).get("event_type"));
        assertEquals("rj-789", events.get(0).get("subject_id"));
    }

    @Test
    void handleWithSingleProvider() {
        NotificationEventHandler singleHandler = new NotificationEventHandler(
                dsl,
                List.of(new NotificationProvider() {
                    @Override public String channel() { return "SMS"; }
                    @Override public String providerCode() { return "stub-sms"; }
                    @Override public DeliveryResult send(DeliveryCommand command) {
                        return new DeliveryResult("SENT", "{\"channel\":\"SMS\"}");
                    }
                }),
                renderingService);

        singleHandler.handle(new NotificationInboundEvent(
                "test.event", "sub-1", Map.of("key", "value")));

        List<Map<String, Object>> deliveries = dsl.select()
                .from(table("notification_delivery"))
                .fetchMaps();
        assertEquals(1, deliveries.size());
        assertEquals("SMS", deliveries.get(0).get("channel"));
    }

    @Test
    void handleWithNoProviders() {
        NotificationEventHandler emptyHandler = new NotificationEventHandler(
                dsl, List.of(), renderingService);

        emptyHandler.handle(new NotificationInboundEvent(
                "test.event", "sub-1", Map.of("key", "value")));

        List<Map<String, Object>> events = dsl.select()
                .from(table("notification_event"))
                .fetchMaps();
        assertEquals(1, events.size(), "Event should still be persisted");

        List<Map<String, Object>> deliveries = dsl.select()
                .from(table("notification_delivery"))
                .fetchMaps();
        assertEquals(0, deliveries.size(), "No deliveries without providers");
    }
}
