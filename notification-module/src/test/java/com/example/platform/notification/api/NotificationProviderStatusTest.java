package com.example.platform.notification.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.example.platform.notification.app.NotificationChannelBindingService;
import com.example.platform.notification.app.NotificationEventCatalogService;
import com.example.platform.notification.app.NotificationEventPublisher;
import com.example.platform.notification.app.NotificationInboxService;
import com.example.platform.notification.app.NotificationPreferenceService;
import com.example.platform.notification.app.NotificationQueryService;
import com.example.platform.notification.app.NotificationSubscriptionService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationProviderStatusTest {

    @Test
    void localProviderIsDisabledWhenNoRealProviderExists() {
        NotificationController controller = new NotificationController(
                mock(NotificationEventPublisher.class), mock(NotificationQueryService.class),
                mock(NotificationEventCatalogService.class), mock(NotificationChannelBindingService.class),
                mock(NotificationSubscriptionService.class), mock(NotificationPreferenceService.class),
                mock(NotificationInboxService.class), null);

        Map<String, Object> status = controller.adminProviderStatus();

        @SuppressWarnings("unchecked")
        Map<String, Object> local = (Map<String, Object>) status.get("local");
        assertFalse((Boolean) local.get("enabled"));
    }
}
