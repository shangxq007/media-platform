package com.example.platform.notification.app;

import com.example.platform.notification.test.NotificationTestBase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationChannelBinding;
import com.example.platform.notification.domain.NotificationSubscription;
import com.example.platform.notification.infrastructure.WebhookUrlValidator;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationAccessDecisionTest extends NotificationTestBase {

    private NotificationChannelBindingService bindingService;
    private NotificationSubscriptionService subscriptionService;
    private AuditPort audit;
    private ErrorCodeRegistry errorCodeRegistry;

    private static final ConfigurableErrorCode CHANNEL_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-003", 4042003,
            Map.of("en", "Notification channel binding not found"),
            "notification", 404);

    private static final ConfigurableErrorCode SUBSCRIPTION_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-002", 4042002,
            Map.of("en", "Notification subscription not found"),
            "notification", 404);

    private static final ConfigurableErrorCode CHANNEL_UNSUPPORTED = new ConfigurableErrorCode(
            "NOTIFICATION-400-003", 4002003,
            Map.of("en", "Notification channel type unsupported"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_URL_INVALID = new ConfigurableErrorCode(
            "NOTIFICATION-400-006", 4002006,
            Map.of("en", "Invalid webhook URL"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_PRIVATE_IP_BLOCKED = new ConfigurableErrorCode(
            "NOTIFICATION-403-001", 4032001,
            Map.of("en", "Webhook URL resolved to private/internal IP and was blocked"),
            "notification", 403);

    private static final ConfigurableErrorCode SUBSCRIBABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-001", 4002001,
            Map.of("en", "Notification event is not subscribable"),
            "notification", 400);

    private static final ConfigurableErrorCode CRITICAL_DISABLE_ERROR = new ConfigurableErrorCode(
            "NOTIFICATION-400-010", 4002010,
            Map.of("en", "Critical notification event cannot be disabled"),
            "notification", 400);

    private static final ConfigurableErrorCode CHANNEL_TEST_FAILED = new ConfigurableErrorCode(
            "NOTIFICATION-400-005", 4002005,
            Map.of("en", "Notification channel test failed"),
            "notification", 400);

    @BeforeEach
    void setUp() {
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

        // Create catalog service with event definitions
        NotificationEventCatalogService catalogService = new NotificationEventCatalogService(dsl);
        catalogService.init();

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
        assertTrue(ex.getErrorCode().code().startsWith("NOTIFICATION-"));
    }

    @Test
    void userCannotSubscribeToSystemOnlyEvent() {
        PlatformException ex = assertThrows(PlatformException.class, () ->
                subscriptionService.createSubscription("user-1", "system.announcement", true, List.of("IN_APP")));

        assertEquals("NOTIFICATION-400-001", ex.getErrorCode().code());
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
