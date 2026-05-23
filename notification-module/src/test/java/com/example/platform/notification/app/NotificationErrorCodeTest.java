package com.example.platform.notification.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationErrorCodeTest {

    private ErrorCodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
    }

    @Test
    void allNotificationErrorCodesExist() {
        String[] expectedCodes = {
                "NOTIFICATION-404-001",
                "NOTIFICATION-400-001",
                "NOTIFICATION-404-002",
                "NOTIFICATION-400-002",
                "NOTIFICATION-404-003",
                "NOTIFICATION-400-003",
                "NOTIFICATION-400-004",
                "NOTIFICATION-400-005",
                "NOTIFICATION-400-006",
                "NOTIFICATION-403-001",
                "NOTIFICATION-400-007",
                "NOTIFICATION-503-001",
                "NOTIFICATION-503-002",
                "NOTIFICATION-400-008",
                "NOTIFICATION-400-009",
                "NOTIFICATION-403-002",
                "NOTIFICATION-402-001",
                "NOTIFICATION-429-001",
                "NOTIFICATION-400-010",
                "NOTIFICATION-400-011"
        };

        for (String code : expectedCodes) {
            ConfigurableErrorCode errorCode = registry.getErrorCode(code).orElse(null);
            assertNotNull(errorCode, "Error code " + code + " should exist in registry");
        }
    }

    @Test
    void eachErrorCodeHasEnglishMessage() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();

        for (Map.Entry<String, ConfigurableErrorCode> entry : allCodes.entrySet()) {
            if (!entry.getKey().startsWith("NOTIFICATION-")) continue;

            ConfigurableErrorCode errorCode = entry.getValue();
            assertTrue(errorCode.messages().containsKey("en"),
                    "Error code " + entry.getKey() + " should have English message");
            assertFalse(errorCode.messages().get("en").isBlank(),
                    "Error code " + entry.getKey() + " English message should not be blank");
        }
    }

    @Test
    void eachErrorCodeHasChineseMessage() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();

        for (Map.Entry<String, ConfigurableErrorCode> entry : allCodes.entrySet()) {
            if (!entry.getKey().startsWith("NOTIFICATION-")) continue;

            ConfigurableErrorCode errorCode = entry.getValue();
            assertTrue(errorCode.messages().containsKey("zh"),
                    "Error code " + entry.getKey() + " should have Chinese message");
            assertFalse(errorCode.messages().get("zh").isBlank(),
                    "Error code " + entry.getKey() + " Chinese message should not be blank");
        }
    }

    @Test
    void criticalCannotDisableCodeHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-400-010");

        assertEquals("Critical notification event cannot be disabled", errorCode.message("en"));
        assertEquals("关键通知事件不可关闭", errorCode.message("zh"));
        assertEquals(400, errorCode.status());
        assertTrue(errorCode.code().contains("NOTIFICATION"));
    }

    @Test
    void webhookPrivateIpBlockedHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-403-001");

        assertTrue(errorCode.message("en").contains("private") || errorCode.message("en").contains("internal"),
                "English message should mention private/internal IP");
        assertFalse(errorCode.message("zh").isBlank(), "Chinese message should exist");
        assertEquals(403, errorCode.status());
    }

    @Test
    void webhookUrlInvalidHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-400-006");

        assertFalse(errorCode.message("en").isBlank(), "English message should exist");
        assertFalse(errorCode.message("zh").isBlank(), "Chinese message should exist");
        assertEquals(400, errorCode.status());
    }

    @Test
    void notSubscribableHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-400-001");

        assertTrue(errorCode.message("en").contains("subscribable") || errorCode.message("en").contains("not"),
                "English message should indicate not subscribable");
        assertFalse(errorCode.message("zh").isBlank(), "Chinese message should exist");
    }

    @Test
    void novuNotConfiguredHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-503-002");

        assertTrue(errorCode.message("en").contains("not configured") || errorCode.message("en").contains("Novu"),
                "English message should indicate Novu not configured");
        assertFalse(errorCode.message("zh").isBlank(), "Chinese message should exist");
        assertEquals(503, errorCode.status());
    }

    @Test
    void allNotificationCodesHaveNumericCode() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();

        for (Map.Entry<String, ConfigurableErrorCode> entry : allCodes.entrySet()) {
            if (!entry.getKey().startsWith("NOTIFICATION-")) continue;

            assertTrue(entry.getValue().numericCode() > 0,
                    "Error code " + entry.getKey() + " should have a positive numeric code");
        }
    }

    @Test
    void allNotificationCodesHaveValidStatus() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();
        Set<Integer> validStatuses = Set.of(400, 401, 402, 403, 404, 405, 408, 409, 410, 422, 425, 429, 500, 502, 503);

        for (Map.Entry<String, ConfigurableErrorCode> entry : allCodes.entrySet()) {
            if (!entry.getKey().startsWith("NOTIFICATION-")) continue;

            assertTrue(validStatuses.contains(entry.getValue().status()),
                    "Error code " + entry.getKey() + " should have a valid HTTP status, got: " + entry.getValue().status());
        }
    }

    @Test
    void allNotificationCodesBelongToNotificationModule() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();

        for (Map.Entry<String, ConfigurableErrorCode> entry : allCodes.entrySet()) {
            if (!entry.getKey().startsWith("NOTIFICATION-")) continue;

            assertEquals("notification", entry.getValue().module(),
                    "Error code " + entry.getKey() + " should belong to notification module");
        }
    }

    @Test
    void errorCodesAreLoadableFromRegistry() {
        Map<String, ConfigurableErrorCode> allCodes = registry.getAllErrorCodes();
        assertFalse(allCodes.isEmpty(), "Registry should have loaded error codes");

        long notificationCount = allCodes.keySet().stream()
                .filter(k -> k.startsWith("NOTIFICATION-"))
                .count();
        assertTrue(notificationCount >= 15,
                "Should have at least 15 notification error codes, found: " + notificationCount);
    }

    @Test
    void getRequiredErrorCodeThrowsForUnknownCode() {
        assertThrows(IllegalStateException.class, () ->
                registry.getRequiredErrorCode("NOTIFICATION-NONEXISTENT-999"));
    }

    @Test
    void messageFallsBackToCodeWhenLocaleMissing() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-400-010");

        String unknownLocale = errorCode.message("fr");
        assertNotNull(unknownLocale, "Should return non-null for unknown locale");
    }

    @Test
    void subscriptionNotFoundHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-404-002");

        assertTrue(errorCode.message("en").contains("not found") || errorCode.message("en").contains("subscription"),
                "English message should indicate subscription not found");
        assertFalse(errorCode.message("zh").isBlank());
        assertEquals(404, errorCode.status());
    }

    @Test
    void channelBindingNotFoundHasCorrectMessages() {
        ConfigurableErrorCode errorCode = registry.getRequiredErrorCode("NOTIFICATION-404-003");

        assertTrue(errorCode.message("en").contains("not found") || errorCode.message("en").contains("binding"),
                "English message should indicate binding not found");
        assertFalse(errorCode.message("zh").isBlank());
        assertEquals(404, errorCode.status());
    }
}
