package com.example.platform.notification.app;

import com.example.platform.notification.api.ingress.NotificationEventPublisher;
import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationIngressContractTest {
    @Test void requiresActualEventTypeAndSubject() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationInboundEvent(" ", "subject", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NotificationInboundEvent("test", null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NotificationInboundEvent("x".repeat(129), "subject", Map.of()));
    }
    @Test void templateExtensionDataIsCopiedAndNullMeansEmpty() {
        var source = new HashMap<String, Object>(); source.put("title", "Original");
        var event = new NotificationInboundEvent("test", "subject", source);
        source.put("title", "Changed");
        assertEquals("Original", event.payload().get("title"));
        assertThrows(UnsupportedOperationException.class, () -> event.payload().put("title", "Changed"));
        assertEquals(Map.of(), new NotificationInboundEvent("test", "subject", null).payload());
    }
    static boolean prohibited(String source) {
        return source.contains("import com.example.platform.notification.app.NotificationEventPublisher;")
                || source.contains("import com.example.platform.notification.domain.NotificationInboundEvent;")
                || source.contains("import com.example.platform.notification.app.NotificationOutboxEvents;");
    }
    @Test void retiredImportsAreRejectedIncludingNegativeControl() throws Exception {
        assertTrue(prohibited("import com.example.platform.notification.app.NotificationEventPublisher;"));
        assertFalse(prohibited("import com.example.platform.notification.api.ingress.NotificationEventPublisher;"));
        try (var sources = java.nio.file.Files.walk(java.nio.file.Path.of(".."))) {
            for (var file : sources.filter(p -> p.toString().contains("/src/main/") && p.toString().endsWith(".java")).toList())
                assertFalse(prohibited(java.nio.file.Files.readString(file)), file.toString());
        }
    }
    @Test void onlyPublishedTypedIngressIsReachable() throws Exception {
        assertEquals(1, NotificationEventPublisher.class.getMethods().length);
        assertArrayEquals(new Class<?>[]{NotificationInboundEvent.class, String.class},
                NotificationEventPublisher.class.getMethods()[0].getParameterTypes());
        assertFalse(NotificationEventPublisher.class.isAssignableFrom(SpringNotificationEventPublisher.class));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.example.platform.notification.app.NotificationEventPublisher"));
    }
}
