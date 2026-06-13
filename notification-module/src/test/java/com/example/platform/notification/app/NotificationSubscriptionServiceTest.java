package com.example.platform.notification.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.notification.domain.NotificationSubscription;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationSubscriptionServiceTest extends PostgresTestContainerSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private Connection conn;
    private NotificationSubscriptionService subscriptionService;
    private NotificationEventCatalogService catalogService;
    private AuditPort audit;
    private ErrorCodeRegistry errorCodeRegistry;

    private static final ConfigurableErrorCode SUBSCRIBABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-001", 4002001,
            Map.of("en", "Notification event is not subscribable", "zh", "通知事件不可订阅"),
            "notification", 400);

    private static final ConfigurableErrorCode CRITICAL_DISABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-010", 4002010,
            Map.of("en", "Critical notification event cannot be disabled", "zh", "关键通知事件不可关闭"),
            "notification", 400);

    private static final ConfigurableErrorCode SUBSCRIPTION_NOT_FOUND_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-404-002", 4042002,
            Map.of("en", "Notification subscription not found", "zh", "通知订阅不存在"),
            "notification", 404);

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
        String dbName = "subtest" + COUNTER.incrementAndGet();
        // Using shared PostgreSQL connection
        // Using shared dsl

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

            stmt.execute("create table notification_subscription ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64),"
                    + "workspace_id varchar(64),"
                    + "user_id varchar(64) not null,"
                    + "event_key varchar(100) not null,"
                    + "enabled boolean not null default true,"
                    + "channels text,"
                    + "frequency varchar(30) not null default 'IMMEDIATE',"
                    + "filters text,"
                    + "quiet_hours_start varchar(10),"
                    + "quiet_hours_end varchar(10),"
                    + "quiet_hours_timezone varchar(50),"
                    + "created_at timestamp not null,"
                    + "updated_at timestamp not null"
                    + ")");
        }

        catalogService = new NotificationEventCatalogService(dsl);
        catalogService.init();

        audit = mock(AuditPort.class);
        errorCodeRegistry = mock(ErrorCodeRegistry.class);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_EVENT_NOT_SUBSCRIBABLE"))
                .thenReturn(SUBSCRIBABLE_ERROR);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"))
                .thenReturn(CRITICAL_DISABLE_ERROR);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_SUBSCRIPTION_NOT_FOUND"))
                .thenReturn(SUBSCRIPTION_NOT_FOUND_ERROR);

        subscriptionService = new NotificationSubscriptionService(dsl, audit, errorCodeRegistry, catalogService);
    }

    @Test
    void canSubscribeToUserConfigurableEvent() {
        NotificationSubscription sub = subscriptionService.createSubscription(
                "user-1", "render.job.completed", true, List.of("IN_APP", "EMAIL"));

        assertNotNull(sub);
        assertEquals("user-1", sub.userId());
        assertEquals("render.job.completed", sub.eventKey());
        assertTrue(sub.enabled());
        verify(audit).record(eq("USER"), eq("NOTIFICATION_SUBSCRIPTION_CREATED"),
                eq("NOTIFICATION"), eq("SUBSCRIPTION"), anyString(), any(Map.class));
    }

    @Test
    void cannotSubscribeToNonConfigurableEvent() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "render.job.requires_review", true, List.of("EMAIL")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void cannotSubscribeToSystemOnlyEvent() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "system.announcement", true, List.of("IN_APP")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void criticalEventNotSubscribableByDefault() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "security.suspicious_activity", false, List.of("EMAIL")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void cannotDisableCriticalEventViaDirectCreate() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "quota.exceeded", false, List.of("EMAIL")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void listSubscribableEventsExcludesSystemOnly() {
        List<NotificationSubscription> subscribable = subscriptionService.listSubscribableEvents("user-1");
        assertFalse(subscribable.isEmpty());

        List<String> keys = subscribable.stream().map(NotificationSubscription::eventKey).toList();
        assertFalse(keys.contains("system.announcement"),
                "SYSTEM_ONLY events should not appear in subscribable list");
        assertFalse(keys.contains("render.job.requires_review"),
                "ADMIN_CONTROLLED events should not appear in subscribable list");
    }

    @Test
    void listSubscribableEventsIncludesUserConfigurable() {
        List<NotificationSubscription> subscribable = subscriptionService.listSubscribableEvents("user-1");
        List<String> keys = subscribable.stream().map(NotificationSubscription::eventKey).toList();

        assertTrue(keys.contains("render.job.completed"),
                "USER_CONFIGURABLE events should appear in subscribable list");
        assertTrue(keys.contains("render.job.failed"),
                "USER_CONFIGURABLE events should appear in subscribable list");
    }

    @Test
    void batchUpdatePartialFailureExplained() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("EMAIL"));

        List<Map<String, Object>> updates = List.of(
                Map.of("eventKey", "render.job.completed", "enabled", false),
                Map.of("eventKey", "system.announcement", "enabled", true)
        );

        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.batchUpdate("user-1", updates));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void batchUpdateSucceedsForAllValid() {
        List<Map<String, Object>> updates = List.of(
                Map.of("eventKey", "render.job.completed", "enabled", true),
                Map.of("eventKey", "render.job.failed", "enabled", false)
        );

        List<NotificationSubscription> results = subscriptionService.batchUpdate("user-1", updates);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(s -> s.eventKey().equals("render.job.completed") && s.enabled()));
        assertTrue(results.stream().anyMatch(s -> s.eventKey().equals("render.job.failed") && !s.enabled()));
    }

    @Test
    void auditRecordGeneratedOnCreate() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("IN_APP"));

        verify(audit).record(
                eq("USER"),
                eq("NOTIFICATION_SUBSCRIPTION_CREATED"),
                eq("NOTIFICATION"),
                eq("SUBSCRIPTION"),
                anyString(),
                argThat((Map<String, Object> payload) ->
                        "user-1".equals(payload.get("userId"))
                                && "render.job.completed".equals(payload.get("eventKey"))
                                && Boolean.TRUE.equals(payload.get("enabled")))
        );
    }

    @Test
    void auditRecordGeneratedOnUpdate() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("IN_APP"));
        reset(audit);

        subscriptionService.updateSubscription("user-1", "render.job.completed", false, List.of("EMAIL"));

        verify(audit).record(
                eq("USER"),
                eq("NOTIFICATION_SUBSCRIPTION_UPDATED"),
                eq("NOTIFICATION"),
                eq("SUBSCRIPTION"),
                anyString(),
                argThat((Map<String, Object> payload) ->
                        "user-1".equals(payload.get("userId"))
                                && "render.job.completed".equals(payload.get("eventKey"))
                                && Boolean.FALSE.equals(payload.get("enabled")))
        );
    }

    @Test
    void listUserSubscriptionsReturnsOnlyOwn() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("EMAIL"));
        subscriptionService.createSubscription("user-2", "render.job.completed", true, List.of("SMS"));

        List<NotificationSubscription> user1Subs = subscriptionService.listUserSubscriptions("user-1");
        assertEquals(1, user1Subs.size());
        assertEquals("user-1", user1Subs.get(0).userId());
    }

    @Test
    void updateNonExistentSubscriptionThrows() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.updateSubscription("user-1", "render.job.completed", true, List.of("EMAIL")));

        assertEquals("NOTIFICATION-404-002", ex.getErrorCode().code());
    }

    @Test
    void upsertCreatesWhenNotExists() {
        NotificationSubscription sub = subscriptionService.upsertSubscription(
                "user-1", "render.job.completed", true, List.of("EMAIL"));

        assertNotNull(sub);
        assertNotNull(sub.subscriptionId());
        assertTrue(sub.subscriptionId().startsWith("nsu"));
    }

    @Test
    void upsertUpdatesWhenExists() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("EMAIL"));

        NotificationSubscription updated = subscriptionService.upsertSubscription(
                "user-1", "render.job.completed", false, List.of("SMS"));

        assertFalse(updated.enabled());
        assertNotNull(updated.subscriptionId());
    }

    @Test
    void subscribableEventsDefaultEnabledMatchesDefinition() {
        List<NotificationSubscription> subscribable = subscriptionService.listSubscribableEvents("user-1");

        for (NotificationSubscription sub : subscribable) {
            assertEquals(true, sub.enabled(),
                    "Subscribable event " + sub.eventKey() + " should default to enabled");
        }
    }
}
