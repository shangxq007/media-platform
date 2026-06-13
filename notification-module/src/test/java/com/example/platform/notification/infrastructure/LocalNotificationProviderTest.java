package com.example.platform.notification.infrastructure;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.app.NotificationDeliveryRepository;
import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LocalNotificationProviderTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private MockNotificationProvider mockProvider;
    private NotificationDeliveryRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS notification_record ("
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

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE notification_record CASCADE");
        repository = new NotificationDeliveryRepository(dsl);
        mockProvider = new MockNotificationProvider(repository);
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
                "evt-1", "EMAIL", "Test subject", "Test body", Map.of());

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
    void sentNotificationContainsAllFields() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-all", "SMS", "Full Subject", "Full Body", Map.of("key1", "val1"));

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        MockNotificationProvider.SentNotification notification = sent.stream()
                .filter(n -> n.eventId().equals("evt-all"))
                .findFirst()
                .orElseThrow();

        assertEquals("SMS", notification.channel());
        assertEquals("Full Subject", notification.subject());
        assertEquals("Full Body", notification.body());
    }

    @Test
    void sendSmsRecordsDelivery() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-sms", "SMS", "SMS Subject", "SMS Body", Map.of());

        mockProvider.send(command);

        List<MockNotificationProvider.SentNotification> sent = mockProvider.getSentNotifications();
        assertTrue(sent.stream().anyMatch(n -> n.eventId().equals("evt-sms") && n.channel().equals("SMS")));
    }

    @Test
    void responsePayloadContainsAcceptedFlag() {
        DeliveryCommand command = new DeliveryCommand(
                "evt-acc", "MOCK", "Subject", "Body", Map.of());

        DeliveryResult result = mockProvider.send(command);

        assertTrue(result.responsePayload().contains("\"accepted\":true"));
    }
}
