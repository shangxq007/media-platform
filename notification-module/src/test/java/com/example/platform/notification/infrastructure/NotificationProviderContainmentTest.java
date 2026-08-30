package com.example.platform.notification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.notification.domain.DeliveryCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.Profile;

class NotificationProviderContainmentTest {

    @Test
    void missingProductionProviderReturnsExplicitFailureNotSent() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        NotificationProviderRouter router = new NotificationProviderRouter(
                List.of(),
                beans.getBeanProvider(NovuNotificationProvider.class),
                beans.getBeanProvider(MockNotificationProvider.class));

        var result = router.route(
                new DeliveryCommand("event-1", "EMAIL", "subject", "body", Map.of()),
                "EMAIL");

        assertEquals("FAILED", result.status());
        assertEquals("{\"error\":\"NOTIFICATION_PROVIDER_UNAVAILABLE\"}", result.responsePayload());
    }

    @Test
    void mockProviderIsTestProfileOnly() {
        Profile profile = MockNotificationProvider.class.getAnnotation(Profile.class);
        assertArrayEquals(new String[] {"test"}, profile.value());
    }
}
