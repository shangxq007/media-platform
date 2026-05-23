package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalNotificationProviderTest {

    private MockNotificationProvider mockProvider;
    private NotificationDeliveryRepositoryTestSupport repositorySupport;

    @BeforeEach
    void setUp() {
        repositorySupport = new NotificationDeliveryRepositoryTestSupport();
        mockProvider = new MockNotificationProvider(repositorySupport.repository);
    }

    @Test
    void mockProviderChannelIsMock() {
        assertEquals("MOCK", mockProvider.channel());
    }

    @Test
    void mockProviderCodeIsMockNotification() {
        assertEquals("mock-notification", mockProvider.providerCode());
    }

    @Test
    void sendReturnsSuccessResult() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-1", "EMAIL", "Test Subject", "Test Body", Map.of());

        DeliveryResult result = mockProvider.send(command);

        assertNotNull(result);
        assertEquals("SENT", result.status());
        assertTrue(result.responsePayload().contains("MOCK"));
    }

    @Test
    void sendPersistsDeliveryRecord() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-2", "SMS", "SMS Subject", "SMS Body", Map.of());

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertTrue(sent.stream().anyMatch(n -> n.eventId().equals("evt-2")),
                "Delivery record should be persisted for SMS channel");
    }

    @Test
    void sendEmailRecordsDelivery() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-email", "EMAIL", "Email Subject", "Email Body", Map.of());

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertTrue(sent.stream().anyMatch(n -> n.eventId().equals("evt-email") && n.channel().equals("EMAIL")),
                "Delivery record should be persisted for EMAIL channel");
    }

    @Test
    void sendSmsRecordsDelivery() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-sms", "SMS", "SMS Subject", "SMS Body", Map.of());

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertTrue(sent.stream().anyMatch(n -> n.eventId().equals("evt-sms") && n.channel().equals("SMS")),
                "Delivery record should be persisted for SMS channel");
    }

    @Test
    void sendWebhookRecordsDelivery() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-wh", "WEBHOOK", "Webhook Subject", "Webhook Body", Map.of());

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertTrue(sent.stream().anyMatch(n -> n.eventId().equals("evt-wh") && n.channel().equals("WEBHOOK")),
                "Delivery record should be persisted for WEBHOOK channel");
    }

    @Test
    void sentNotificationContainsAllFields() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-full", "EMAIL", "Full Subject", "Full Body",
                Map.of("key", "value"));

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertEquals(1, sent.size());

        MockNotificationProvider.SentNotification notification = sent.get(0);
        assertEquals("evt-full", notification.eventId());
        assertEquals("EMAIL", notification.channel());
        assertEquals("Full Subject", notification.subject());
        assertEquals("Full Body", notification.body());
        assertNotNull(notification.sentAt());
    }

    @Test
    void multipleSendsAccumulateRecords() {
        mockProvider.send(new DeliveryCommand("evt-1", "MOCK", "S1", "B1", Map.of()));
        mockProvider.send(new DeliveryCommand("evt-2", "MOCK", "S2", "B2", Map.of()));
        mockProvider.send(new DeliveryCommand("evt-3", "MOCK", "S3", "B3", Map.of()));

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertEquals(3, sent.size());
    }

    @Test
    void clearIsNoOp() {
        assertDoesNotThrow(() -> mockProvider.clear());
    }

    @Test
    void responsePayloadContainsAcceptedFlag() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-acc", "MOCK", "Subject", "Body", Map.of());

        DeliveryResult result = mockProvider.send(command);

        assertTrue(result.responsePayload().contains("\"accepted\":true"));
    }

    static class NotificationDeliveryRepositoryTestSupport {
        final com.example.platform.notification.app.NotificationDeliveryRepository repository;

        NotificationDeliveryRepositoryTestSupport() {
            try {
                java.sql.Connection conn = java.sql.DriverManager.getConnection(
                        "jdbc:h2:mem:localprovtest" + Instant.now().toEpochMilli()
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
                org.jooq.DSLContext dsl = org.jooq.impl.DSL.using(conn, org.jooq.SQLDialect.H2);

                try (java.sql.Statement stmt = conn.createStatement()) {
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

                repository = new com.example.platform.notification.app.NotificationDeliveryRepository(dsl);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
