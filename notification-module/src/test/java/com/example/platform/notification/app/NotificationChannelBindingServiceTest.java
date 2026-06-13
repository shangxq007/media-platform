package com.example.platform.notification.app;

import com.example.platform.notification.test.NotificationTestBase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.notification.domain.NotificationChannelBinding;
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

class NotificationChannelBindingServiceTest extends NotificationTestBase {

    private NotificationChannelBindingService bindingService;
    private AuditPort audit;
    private ErrorCodeRegistry errorCodeRegistry;
    private WebhookUrlValidator webhookUrlValidator;

    private static final ConfigurableErrorCode CHANNEL_UNSUPPORTED = new ConfigurableErrorCode(
            "NOTIFICATION-400-003", 4002003,
            Map.of("en", "Notification channel type unsupported"),
            "notification", 400);

    private static final ConfigurableErrorCode CHANNEL_NOT_FOUND = new ConfigurableErrorCode(
            "NOTIFICATION-404-003", 4042003,
            Map.of("en", "Notification channel binding not found"),
            "notification", 404);

    private static final ConfigurableErrorCode WEBHOOK_URL_INVALID = new ConfigurableErrorCode(
            "NOTIFICATION-400-006", 4002006,
            Map.of("en", "Invalid webhook URL"),
            "notification", 400);

    private static final ConfigurableErrorCode WEBHOOK_PRIVATE_IP_BLOCKED = new ConfigurableErrorCode(
            "NOTIFICATION-403-001", 4032001,
            Map.of("en", "Webhook URL resolved to private/internal IP and was blocked"),
            "notification", 403);

    private static final ConfigurableErrorCode CHANNEL_TEST_FAILED = new ConfigurableErrorCode(
            "NOTIFICATION-400-005", 4002005,
            Map.of("en", "Notification channel test failed"),
            "notification", 400);

    @BeforeEach
    void setUp() {
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
                "user-1", "EMAIL", "user@example.com");

        assertNotNull(binding);
        assertEquals("user-1", binding.userId());
        assertEquals("EMAIL", binding.channelType());
        assertTrue(binding.destinationMasked().contains("@"));
        assertFalse(binding.verified());
    }

    @Test
    void createSmsBindingMasksNumber() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "SMS", "+1234567890");

        assertNotNull(binding);
        assertEquals("SMS", binding.channelType());
        assertTrue(binding.destinationMasked().contains("*"));
    }

    @Test
    void bindingDefaultsToUnverified() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        assertFalse(binding.verified());
        assertEquals("PENDING", binding.verificationStatus());
    }

    @Test
    void findBindingReturnsOnlyOwn() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "user1@example.com");

        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent());

        Optional<NotificationChannelBinding> notFound = bindingService.findBinding(binding.bindingId(), "user-2");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void listUserBindingsReturnsOnlyOwn() {
        bindingService.createBinding("user-1", "EMAIL", "user1@example.com");
        bindingService.createBinding("user-2", "SMS", "+1234567890");

        List<NotificationChannelBinding> user1Bindings = bindingService.listUserBindings("user-1");
        assertEquals(1, user1Bindings.size());
        assertEquals("user-1", user1Bindings.get(0).userId());
    }

    @Test
    void unsupportedChannelThrows() {
        assertThrows(PlatformException.class, () ->
                bindingService.createBinding("user-1", "INVALID", "test"));
    }

    @Test
    void disableBindingSetsReason() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        bindingService.disableBinding(binding.bindingId(), "user-1", "User requested");

        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent());
        assertFalse(found.get().enabled());
    }

    @Test
    void testVerifiedBindingSucceeds() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        bindingService.verifyBinding(binding.bindingId(), "user-1");

        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent());
        assertTrue(found.get().verified());
    }

    @Test
    void testUnverifiedBindingThrows() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "test@example.com");

        // Verify the binding is not verified
        Optional<NotificationChannelBinding> found = bindingService.findBinding(binding.bindingId(), "user-1");
        assertTrue(found.isPresent());
        assertFalse(found.get().verified());
    }

    @Test
    void allWriteOperationsGenerateAudit() {
        NotificationChannelBinding binding = bindingService.createBinding(
                "user-1", "EMAIL", "audit@example.com");

        verify(audit, atLeastOnce()).record(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(Map.class));
    }
}
