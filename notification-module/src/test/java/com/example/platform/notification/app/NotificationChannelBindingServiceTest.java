package com.example.platform.notification.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationChannelBinding;
import com.example.platform.notification.infrastructure.WebhookUrlValidator;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationChannelBindingServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private Connection conn;
    private NotificationChannelBindingService bindingService;
    private AuditPort audit;
    private ErrorCodeRegistry errorCodeRegistry;
    private WebhookUrlValidator webhookUrlValidator;

    private static final ConfigurableErrorCode CHANNEL_UNSUPPORTED = new ConfigurableErrorCode(
            "NOTIFICATION-400-003", 4002003,
            Map.of("en", "Notification channel type unsupported", "zh", "不支持的通知渠道类型"),
            "notification", 400);

    private static final ConfigurableErrorCode CHANNEL_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-003", 4042003,
            Map.of("en", "Notification channel binding not found", "zh", "通知渠道绑定不存在"),
            "notification", 404);

    private static final ConfigurableErrorCode WEBHOOK_URL_INVALID = new ConfigurableErrorCode(
            "NOTIFICATION-400-006", 4002006,
            Map.of("en", "Invalid webhook URL", "zh", "Webhook URL 无效"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_PRIVATE_IP_BLOCKED = new ConfigurableErrorCode(
            "NOTIFICATION-403-001", 4032001,
            Map.of("en", "Webhook URL resolved to private/internal IP and was blocked",
                    "zh", "Webhook URL 解析到内部/私有 IP，已被阻止"),
            "notification", 403);

    private static final ConfigurableErrorCode CHANNEL_TEST_FAILED = new ConfigurableErrorCode(
            "NOTIFICATION-400-005", 4002005,
            Map.of("en", "Notification channel test failed", "zh", "通知渠道测试失败"),
            "notification", 400);

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "bindingtest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
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

        audit = mock(AuditPort.class);
        errorCodeRegistry = mock(ErrorCodeRegistry.class);
        webhookUrlValidator = mock(WebhookUrlValidator.class);

        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_UNSUPPORTED"))
                .thenReturn(CHANNEL_UNSUPPORTED);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_NOT_FOUND"))
                .thenReturn(CHANNEL_NOT_FOUND);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_WEBHOOK_URL_INVALID"))
                .thenReturn(WEBHOOK_URL_INVALID);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_WEBHOOK_PRIVATE_IP_BLOCKED"))
                .thenReturn(WEBHOOK_PRIVATE_IP_BLOCKED);
        when(errorCodeRegistry.getRequiredErrorCode("NOTIFICATION_CHANNEL_TEST_FAILED"))
                .thenReturn(CHANNEL_TEST_FAILED);

        bindingService = new NotificationChannelBindingService(dsl, audit, errorCodeRegistry, webhookUrlValidator);
    }

    @Test
    void createEmailBindingMasksAddress() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "john.doe@example.com");

        assertNotNull(binding);
        assertEquals("EMAIL", binding.channelType());
        assertTrue(binding.destinationMasked().contains("***"), "Email should be masked");
        assertTrue(binding.destinationMasked().contains("@example.com"), "Domain should be visible");
        assertFalse(binding.destinationMasked().contains("john.doe"), "Local part should be masked");
        assertNotNull(binding.destinationEncrypted(), "Encrypted destination should be stored");
        assertTrue(binding.destinationEncrypted().contains("john.doe"), "Encrypted should contain full address");
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_BOUND"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
    }

    @Test
    void createSmsBindingMasksNumber() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "SMS", "+1234567890");

        assertNotNull(binding);
        assertEquals("SMS", binding.channelType());
        assertTrue(binding.destinationMasked().startsWith("***"), "SMS should start with ***");
        assertTrue(binding.destinationMasked().endsWith("7890"), "Last 4 digits should be visible");
        assertFalse(binding.destinationMasked().contains("+123456"), "Prefix should be masked");
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_BOUND"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
    }

    @Test
    void createWebhookBindingStoresUrl() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "WEBHOOK", "https://hooks.example.com/notify");

        assertNotNull(binding);
        assertEquals("WEBHOOK", binding.channelType());
        assertNotNull(binding.destinationEncrypted());
        verify(webhookUrlValidator).validate(
                eq("https://hooks.example.com/notify"),
                eq(WEBHOOK_URL_INVALID),
                eq(WEBHOOK_PRIVATE_IP_BLOCKED));
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_BOUND"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
    }

    @Test
    void webhookUrlValidatorCalledBeforePersist() {
        doThrow(new PlatformException(WEBHOOK_PRIVATE_IP_BLOCKED, "Blocked"))
                .when(webhookUrlValidator).validate(anyString(), any(), any());

        assertThrows(PlatformException.class, () ->
                bindingService.createBinding("user-1", "WEBHOOK", "http://192.168.1.1/hook"));

        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void webhookSsrfBlockedWithCorrectErrorCode() {
        doThrow(new PlatformException(WEBHOOK_PRIVATE_IP_BLOCKED, "Private IP blocked"))
                .when(webhookUrlValidator).validate(anyString(), any(), any());

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.createBinding("user-1", "WEBHOOK", "http://10.0.0.1/hook"));

        assertEquals("NOTIFICATION-403-001", ex.getErrorCode().code());
    }

    @Test
    void unsupportedChannelThrows() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.createBinding("user-1", "PIGEON_MAIL", "some-dest"));

        assertEquals("NOTIFICATION-400-003", ex.getErrorCode().code());
    }

    @Test
    void allWriteOperationsGenerateAudit() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");
        reset(audit);

        bindingService.verifyBinding(binding.bindingId(), "user-1");
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_VERIFIED"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
        reset(audit);

        bindingService.disableBinding(binding.bindingId(), "user-1", "user request");
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_DISABLED"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
        reset(audit);

        bindingService.deleteBinding(binding.bindingId(), "user-1");
        verify(audit).record(eq("USER"), eq("NOTIFICATION_CHANNEL_DELETED"),
                eq("NOTIFICATION"), eq("CHANNEL_BINDING"), anyString(), any(Map.class));
    }

    @Test
    void listUserBindingsReturnsOnlyOwn() {
        bindingService.createBinding("user-1", "EMAIL", "user1@example.com");
        bindingService.createBinding("user-2", "EMAIL", "user2@example.com");

        List<NotificationChannelBinding> user1Bindings = bindingService.listUserBindings("user-1");
        assertEquals(1, user1Bindings.size());
        assertEquals("user-1", user1Bindings.get(0).userId());
    }

    @Test
    void findBindingReturnsOnlyOwn() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent());

        Optional<NotificationChannelBinding> notFound = bindingService.findBinding(binding.bindingId(), "user-2");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void disableBindingSetsReason() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        NotificationChannelBinding disabled = bindingService.disableBinding(
                binding.bindingId(), "user-1", "too many bounces");

        assertFalse(disabled.enabled());
        assertEquals("too many bounces", disabled.disabledReason());
    }

    @Test
    void updateBindingWithNewDestinationValidatesWebhook() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "old@example.com");
        reset(webhookUrlValidator);

        NotificationChannelBinding updated = bindingService.updateBinding(
                binding.bindingId(), "user-1", "new@example.com");

        assertNotNull(updated);
        verify(webhookUrlValidator, never()).validate(anyString(), any(), any());
    }

    @Test
    void updateWebhookBindingRevalidatesUrl() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "WEBHOOK", "https://hooks.example.com/old");
        reset(webhookUrlValidator);

        bindingService.updateBinding(binding.bindingId(), "user-1", "https://hooks.example.com/new");

        verify(webhookUrlValidator).validate(
                eq("https://hooks.example.com/new"),
                eq(WEBHOOK_URL_INVALID),
                eq(WEBHOOK_PRIVATE_IP_BLOCKED));
    }

    @Test
    void testUnverifiedBindingThrows() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        PlatformException ex = assertThrows(PlatformException.class, () ->
                bindingService.testBinding(binding.bindingId(), "user-1"));

        assertEquals("NOTIFICATION-400-005", ex.getErrorCode().code());
    }

    @Test
    void testVerifiedBindingSucceeds() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");
        bindingService.verifyBinding(binding.bindingId(), "user-1");

        NotificationChannelBinding result = bindingService.testBinding(binding.bindingId(), "user-1");
        assertNotNull(result);
        assertTrue(result.verified());
    }

    @Test
    void bindingDefaultsToUnverified() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        assertFalse(binding.verified());
        assertEquals("PENDING", binding.verificationStatus());
    }
}
