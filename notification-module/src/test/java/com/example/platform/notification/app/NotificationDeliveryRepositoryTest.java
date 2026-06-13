package com.example.platform.notification.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.notification.testsupport.NotificationTestSchemaFixture;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.notification.infrastructure.MockNotificationProvider.SentNotification;
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
