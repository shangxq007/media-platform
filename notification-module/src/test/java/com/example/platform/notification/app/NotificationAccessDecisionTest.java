package com.example.platform.notification.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationChannelBinding;
import com.example.platform.notification.domain.NotificationSubscription;
import com.example.platform.notification.infrastructure.WebhookUrlValidator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationAccessDecisionTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private Connection conn;
    private NotificationChannelBindingService bindingService;
    private NotificationSubscriptionService subscriptionService;
    private AuditPort audit;
    private ErrorCodeRegistry errorCodeRegistry;

    private static final ConfigurableErrorCode CHANNEL_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-003", 4042003,
            Map.of("en", "Notification channel binding not found", "zh", "通知渠道绑定不存在"),
            "notification", 404);

    private static final ConfigurableErrorCode SUBSCRIPTION_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-002", 4042002,
            Map.of("en", "Notification subscription not found", "zh", "通知订阅不存在"),
            "notification", 404);

    private static final ConfigurableErrorCode CHANNEL_UNSUPPORTED = new ConfigurableErrorCode(
            "NOTIFICATION-400-003", 4002003,
            Map.of("en", "Notification channel type unsupported", "zh", "不支持的通知渠道类型"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_URL_INVALID = new ConfigurableErrorCode(
            "NOTIFICATION-400-006", 4002006,
            Map.of("en", "Invalid webhook URL", "zh", "Webhook URL 无效"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_PRIVATE_IP_BLOCKED = new ConfigurableErrorCode(
            "NOTIFICATION-403-001", 4032001,
            Map.of("en", "Webhook URL resolved to private/internal IP and was blocked",
                    "zh", "Webhook URL 解析到内部/私有 IP，已被阻止"),
            "notification", 403);

    private static final ConfigurableErrorCode SUBSCRIBABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-001", 4002001,
            Map.of("en", "Notification event is not subscribable", "zh", "通知事件不可订阅"),
            "notification", 400);

    private static final ConfigurableErrorCode CRITICAL_DISABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-010", 4002010,
            Map.of("en", "Critical notification event cannot be disabled", "zh", "关键通知事件不可关闭"),
            "notification", 400);

    private static final ConfigurableErrorCode CHANNEL_TEST_FAILED = new ConfigurableErrorCode(
            "NOTIFICATION-400-005", 4002005,
            Map.of("en", "Notification channel test failed", "zh", "通知渠道测试失败"),
            "notification", 400);

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "accesstest" + COUNTER.incrementAndGet();
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

            stmt.execute("create table notification_channel_binding ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64),"
                    + "workspace_id varchar(64),"
                    + "user_id varchar(64) not null,"
                    + "channel_type varchar(30) not null,"
                    + "destination_masked varchar(500),"
                    + "destination_encrypted text,"
                    + "verified boolean not null default false,"
                    + "verification_status varchar(30) not null default 'PENDING',"
                    + "enabled boolean not null default true,"
                    + "provider varchar(50),"
                    + "failure_count int not null default 0,"
                    + "disabled_reason varchar(200),"
                    + "created_at timestamp not null,"
                    + "updated_at timestamp not null,"
                    + "last_verified_at timestamp"
                    + ")");
        }

        NotificationEventCatalogService catalogService = new NotificationEventCatalogService(dsl);
        catalogService.init();

        audit = mock(AuditPort.class);
        errorCodeRegistry = mock(ErrorCodeRegistry.class);
        WebhookUrlValidator webhookUrlValidator = mock(WebhookUrlValidator.class);

        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"))
                .thenReturn(CHANNEL_NOT_FOUND);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_SUBSCRIPTION_NOT_FOUND"))
                .thenReturn(SUBSCRIPTION_NOT_FOUND);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_UNSUPPORTED"))
                .thenReturn(CHANNEL_UNSUPPORTED);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_WEBHOOK_URL_INVALID"))
                .thenReturn(WEBHOOK_URL_INVALID);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_WEBHOOK_PRIVATE_IP_BLOCKED"))
                .thenReturn(WEBHOOK_PRIVATE_IP_BLOCKED);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_EVENT_NOT_SUBSCRIBABLE"))
                .thenReturn(SUBSCRIBABLE_ERROR);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CRITICAL_CANNOT_DISABLE"))
                .thenReturn(CRITICAL_DISABLE_ERROR);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_TEST_FAILED"))
                .thenReturn(CHANNEL_TEST_FAILED);

        bindingService = new NotificationChannelBindingService(dsl, audit, errorCodeRegistry, webhookUrlValidator);
        subscriptionService = new NotificationSubscriptionService(dsl, audit, errorCodeRegistry, catalogService);
    }

    @Test
    void userCanOnlyAccessOwnSubscriptions() {
        subscriptionService.createSubscription("user-1", "render.job.completed", true, List.of("EMAIL"));
        subscriptionService.createSubscription("user-2", "render.job.completed", true, List.of("SMS"));

        List<NotificationSubscription> user1Subs = subscriptionService.listUserSubscriptions("user-1");
        assertEquals(1, user1Subs.size());
        assertEquals("user-1", user1Subs.get(0).userId());

        List<NotificationSubscription> user2Subs = subscriptionService.listUserSubscriptions("user-2");
        assertEquals(1, user2Subs.size());
        assertEquals("user-2", user2Subs.get(0).userId());
    }

    @Test
    void userCannotAccessOthersChannelBindings() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent(), "Owner should find their own binding");

        Optional<NotificationChannelBinding> notFound = bindingService.findBinding(binding.bindingId(), "user-2");
        assertTrue(notFound.isEmpty(), "Other user should not find someone else's binding");
    }

    @Test
    void userCannotUpdateOthersBinding() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.updateBinding(binding.bindingId(), "user-2", "hacked@evil.com"));

        assertEquals("NOTIFICATION-404-003", ex.getErrorCode().code());
    }

    @Test
    void userCannotDeleteOthersBinding() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.deleteBinding(binding.bindingId(), "user-2"));

        assertEquals("NOTIFICATION-404-003", ex.getErrorCode().code());
    }

    @Test
    void userCannotDisableOthersBinding() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.disableBinding(binding.bindingId(), "user-2", "malicious"));

        assertEquals("NOTIFICATION-404-003", ex.getErrorCode().code());
    }

    @Test
    void userCannotVerifyOthersBinding() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.verifyBinding(binding.bindingId(), "user-2"));

        assertEquals("NOTIFICATION-404-003", ex.getErrorCode().code());
    }

    @Test
    void listBindingsReturnsOnlyOwn() {
        bindingService.createBinding("user-1", "EMAIL", "user1@example.com");
        bindingService.createBinding("user-2", "SMS", "+1234567890");

        List<NotificationChannelBinding> user1Bindings = bindingService.listUserBindings("user-1");
        assertEquals(1, user1Bindings.size());
        assertEquals("user-1", user1Bindings.get(0).userId());
        assertEquals("EMAIL", user1Bindings.get(0).channelType());
    }

    @Test
    void accessDeniedReturnsReasonCode() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.deleteBinding(binding.bindingId(), "user-2"));

        assertNotNull(ex.getErrorCode());
        assertNotNull(ex.getErrorCode().code());
        assertTrue(ex.getErrorCode().code().startsWith("NOTIFICATION-"),
                "Error code should be a notification error code");
    }

    @Test
    void subscribableEventsOnlyReturnsUserAccessible() {
        List<NotificationSubscription> subscribable = subscriptionService.listSubscribableEvents("user-1");
        assertFalse(subscribable.isEmpty());

        List<String> keys = subscribable.stream().map(NotificationSubscription::eventKey).toList();
        assertFalse(keys.contains("system.announcement"),
                "SYSTEM_ONLY events should not be subscribable by regular users");
        assertFalse(keys.contains("render.job.requires_review"),
                "ADMIN_CONTROLLED events should not be subscribable by regular users");
    }

    @Test
    void userCannotSubscribeToSystemOnlyEvent() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "system.announcement", true, List.of("IN_APP")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
        assertNotNull(ex.getErrorCode().code());
    }

    @Test
    void criticalEventNotSubscribable() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "security.suspicious_activity", false, List.of("EMAIL")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
    }

    @Test
    void errorCodeHasCorrectModule() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.deleteBinding(binding.bindingId(), "user-2"));

        assertTrue(ex.getErrorCode().code().startsWith("NOTIFICATION-"));
    }
}
