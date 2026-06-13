package com.example.platform.notification.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.notification.infrastructure.MockNotificationProvider.SentNotification;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryRepositoryTest extends PostgresTestContainerSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private NotificationDeliveryRepository repository;
    private Connection conn;

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
        String dbName = "notifdeltest" + COUNTER.incrementAndGet();
        // Using shared PostgreSQL connection
        // Using shared dsl

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table notification_record ("
                    + "id varchar(64) primary key,"
                    + "event_id varchar(64) not null,"
                    + "channel varchar(32) not null,"
                    + "provider_code varchar(64) not null,"
                    + "status varchar(32) not null,"
                    + "subject varchar(512),"
                    + "body text,"
                    + "metadata_json text,"
                    + "attempt_count int not null default 1,"
                    + "created_at timestamp not null"
                    + ")");
        }

        repository = new NotificationDeliveryRepository(dsl);
    }

    @Test
    void recordDeliveryPersistsNotification() {
        SentNotification sent = new SentNotification("evt_1", "MOCK", "Test Subject", "Test Body", Instant.now());
        String id = repository.recordDelivery(sent);

        assertNotNull(id);
        assertTrue(id.startsWith("ndr_"));

        List<SentNotification> recent = repository.recentDeliveries(10);
        assertEquals(1, recent.size());
        assertEquals("evt_1", recent.get(0).eventId());
        assertEquals("MOCK", recent.get(0).channel());
        assertEquals("Test Subject", recent.get(0).subject());
    }

    @Test
    void recentDeliveriesRespectsLimit() {
        repository.recordDelivery(new SentNotification("evt_1", "MOCK", "S1", "B1", Instant.now()));
        repository.recordDelivery(new SentNotification("evt_2", "MOCK", "S2", "B2", Instant.now()));
        repository.recordDelivery(new SentNotification("evt_3", "MOCK", "S3", "B3", Instant.now()));

        List<SentNotification> recent = repository.recentDeliveries(2);
        assertEquals(2, recent.size());
    }

    @Test
    void recentDeliveriesReturnsEmptyWhenNone() {
        List<SentNotification> recent = repository.recentDeliveries(10);
        assertTrue(recent.isEmpty());
    }
}
