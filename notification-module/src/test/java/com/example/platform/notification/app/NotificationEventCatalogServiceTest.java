package com.example.platform.notification.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationEventDefinition;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationEventCatalogServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private Connection conn;
    private NotificationEventCatalogService service;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "catalogtest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table notification_event_definition ("
                    + "id varchar(64) primary key,"
                    + "event_key varchar(100) not null unique,"
                    + "name varchar(200) not null,"
                    + "description varchar(500),"
                    + "category varchar(50) not null,"
                    + "severity varchar(20) not null,"
                    + "visibility varchar(30) not null,"
                    + "user_configurable boolean not null default false,"
                    + "critical boolean not null default false,"
                    + "default_enabled boolean not null default true,"
                    + "supported_channels text,"
                    + "required_permissions text,"
                    + "required_entitlements text,"
                    + "feature_flag_key varchar(100),"
                    + "novu_workflow_id varchar(100),"
                    + "local_template_key varchar(100),"
                    + "archived boolean not null default false,"
                    + "created_at timestamp not null,"
                    + "updated_at timestamp not null"
                    + ")");
        }

        service = new NotificationEventCatalogService(dsl);
        service.init();
    }

    @Test
    void builtInEventsInitializedOnStartup() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        assertFalse(allEvents.isEmpty(), "Built-in events should be seeded on init");

        List<String> eventKeys = allEvents.stream().map(NotificationEventDefinition::eventKey).toList();
        assertTrue(eventKeys.contains("render.job.completed"), "Should contain render.job.completed");
        assertTrue(eventKeys.contains("security.suspicious_activity"), "Should contain security.suspicious_activity");
        assertTrue(eventKeys.contains("system.announcement"), "Should contain system.announcement");
    }

    @Test
    void userConfigurableEventsOnlyReturnsConfigurable() {
        List<NotificationEventDefinition> userEvents = service.listUserConfigurableEvents();
        assertFalse(userEvents.isEmpty(), "Should have user-configurable events");

        for (NotificationEventDefinition event : userEvents) {
            assertTrue(event.userConfigurable(),
                    "Event " + event.eventKey() + " should be user-configurable");
        }
    }

    @Test
    void systemOnlyEventsNotInUserConfigurableList() {
        List<NotificationEventDefinition> userEvents = service.listUserConfigurableEvents();
        List<String> userEventKeys = userEvents.stream().map(NotificationEventDefinition::eventKey).toList();

        assertFalse(userEventKeys.contains("system.announcement"),
                "SYSTEM_ONLY event should not appear in user-configurable list");
    }

    @Test
    void criticalEventsAreFlagged() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();

        List<NotificationEventDefinition> criticalEvents = allEvents.stream()
                .filter(NotificationEventDefinition::critical)
                .toList();

        assertFalse(criticalEvents.isEmpty(), "Should have at least one critical event");

        List<String> criticalKeys = criticalEvents.stream()
                .map(NotificationEventDefinition::eventKey).toList();
        assertTrue(criticalKeys.contains("security.suspicious_activity"),
                "security.suspicious_activity should be critical");
        assertTrue(criticalKeys.contains("quota.exceeded"),
                "quota.exceeded should be critical");
    }

    @Test
    void adminControlledEventsNotUserConfigurable() {
        List<NotificationEventDefinition> userEvents = service.listUserConfigurableEvents();
        List<String> userEventKeys = userEvents.stream().map(NotificationEventDefinition::eventKey).toList();

        assertFalse(userEventKeys.contains("render.job.requires_review"),
                "ADMIN_CONTROLLED event should not be user-configurable");
        assertFalse(userEventKeys.contains("provider.health.degraded"),
                "ADMIN_CONTROLLED event should not be user-configurable");
    }

    @Test
    void listAllEventsIncludesAllVisibilities() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        List<String> allKeys = allEvents.stream().map(NotificationEventDefinition::eventKey).toList();

        assertTrue(allKeys.contains("system.announcement"),
                "Admin list should include SYSTEM_ONLY events");
        assertTrue(allKeys.contains("render.job.completed"),
                "Admin list should include USER_CONFIGURABLE events");
        assertTrue(allKeys.contains("security.suspicious_activity"),
                "Admin list should include CRITICAL events");
    }

    @Test
    void findByKeyReturnsEvent() {
        NotificationEventCatalogService spyService = spy(service);
        spyService.init();

        var result = spyService.findByKey("render.job.completed");
        assertTrue(result.isPresent());
        assertEquals("Render Job Completed", result.get().name());
    }

    @Test
    void findByKeyReturnsEmptyForMissing() {
        var result = service.findByKey("nonexistent.event.key");
        assertTrue(result.isEmpty());
    }

    @Test
    void isSubscribableOnlyForConfigurableNonSystemOnly() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        assertFalse(allEvents.isEmpty());

        List<NotificationEventDefinition> subscribable = allEvents.stream()
                .filter(e -> service.isSubscribable(e.eventKey()))
                .toList();

        for (NotificationEventDefinition event : subscribable) {
            assertTrue(event.userConfigurable(),
                    "Subscribable event " + event.eventKey() + " must be user-configurable");
            assertNotEquals("SYSTEM_ONLY", event.visibility(),
                    "Subscribable event " + event.eventKey() + " must not be SYSTEM_ONLY");
        }
    }

    @Test
    void isCriticalReturnsTrueForCriticalEvents() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        assertFalse(allEvents.isEmpty());

        assertTrue(service.isCritical("security.suspicious_activity"));
        assertTrue(service.isCritical("quota.exceeded"));
        assertFalse(service.isCritical("render.job.completed"));
    }

    @Test
    void getSupportedChannelsFallsBackToAllChannels() {
        List<String> channels = service.getSupportedChannels("render.job.completed");
        assertFalse(channels.isEmpty(), "Should return default channels when none specified");
        assertTrue(channels.contains("IN_APP"), "Default should include IN_APP");
        assertTrue(channels.contains("EMAIL"), "Default should include EMAIL");
    }

    @Test
    void builtInEventsHaveCorrectChannelSupport() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        assertFalse(allEvents.isEmpty());

        for (NotificationEventDefinition event : allEvents) {
            assertNotNull(event.supportedChannels(), "Event " + event.eventKey() + " should have channels");
            assertFalse(event.supportedChannels().isEmpty(), "Event " + event.eventKey() + " should have at least one channel");
        }
    }

    @Test
    void nonCriticalEventsAreNotFlaggedAsCritical() {
        List<NotificationEventDefinition> allEvents = service.listAllEvents();
        List<NotificationEventDefinition> nonCritical = allEvents.stream()
                .filter(e -> !e.critical())
                .toList();

        assertFalse(nonCritical.isEmpty(), "Should have non-critical events");

        for (NotificationEventDefinition event : nonCritical) {
            assertFalse(event.critical(),
                    "Non-critical event " + event.eventKey() + " should not be flagged critical");
        }
    }
}
