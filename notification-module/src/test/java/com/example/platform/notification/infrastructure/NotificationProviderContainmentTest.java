package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.notification.app.NotificationEventHandler;
import com.example.platform.notification.domain.DeliveryCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class NotificationProviderContainmentTest {

    @Test
    void missingProductionProviderReturnsExplicitFailureNotSent() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        NotificationProviderRouter router = new NotificationProviderRouter(
                List.of(),
                beans.getBeanProvider(NovuNotificationProvider.class));

        var result = router.route(
                new DeliveryCommand("event-1", "EMAIL", "subject", "body", Map.of()),
                "EMAIL");

        assertEquals("FAILED", result.status());
        assertEquals("{\"error\":\"NOTIFICATION_PROVIDER_UNAVAILABLE\"}", result.responsePayload());
    }

    @Test
    void mockProviderDoesNotExistInProductionSourceSet() {
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/com/example/platform/notification/infrastructure/MockNotificationProvider.java")));
    }

    @Test
    void legacyGlobalEventHandlerIsNotRuntimeWired() {
        assertFalse(NotificationEventHandler.class.isAnnotationPresent(
                org.springframework.stereotype.Component.class));
    }
}
