package com.example.platform.notification.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.web.ConfigurableErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationErrorCodeTest {
    private static final List<ConfigurableErrorCode> DIRECT_SERVICE_CODES = List.of(
            NotificationErrorCodes.EVENT_NOT_SUBSCRIBABLE,
            NotificationErrorCodes.SUBSCRIPTION_NOT_FOUND,
            NotificationErrorCodes.CHANNEL_NOT_FOUND,
            NotificationErrorCodes.CHANNEL_UNSUPPORTED,
            NotificationErrorCodes.CHANNEL_TEST_FAILED,
            NotificationErrorCodes.WEBHOOK_URL_INVALID,
            NotificationErrorCodes.WEBHOOK_PRIVATE_IP_BLOCKED,
            NotificationErrorCodes.CRITICAL_CANNOT_DISABLE);

    @Test
    void serviceErrorCodesAreOwnedAndLocalizedByNotification() {
        for (ConfigurableErrorCode code : DIRECT_SERVICE_CODES) {
            assertEquals("notification", code.module());
            assertFalse(code.message("en").isBlank());
            assertFalse(code.message("zh").isBlank());
            assertTrue(code.numericCode() > 0);
            assertEquals(code.status(), Integer.parseInt(code.code().split("-")[1]));
        }
    }

    @Test
    void criticalAndWebhookCodesPreserveTransportDetails() {
        assertEquals("Critical notification event cannot be disabled",
                NotificationErrorCodes.CRITICAL_CANNOT_DISABLE.message("en"));
        assertEquals("关键通知事件不可关闭",
                NotificationErrorCodes.CRITICAL_CANNOT_DISABLE.message("zh"));
        assertEquals(403, NotificationErrorCodes.WEBHOOK_PRIVATE_IP_BLOCKED.status());
        assertTrue(NotificationErrorCodes.WEBHOOK_PRIVATE_IP_BLOCKED.message("en").contains("private"));
    }
}
