package com.example.platform.notification.app;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private NotificationDeliveryRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "notifdeltest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

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
