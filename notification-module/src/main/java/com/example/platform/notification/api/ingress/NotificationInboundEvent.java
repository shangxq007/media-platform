package com.example.platform.notification.api.ingress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Version-1 Notification template input; payload contains template extension data only. */
public record NotificationInboundEvent(String eventType, String subjectId, Map<String, Object> payload) {
    public NotificationInboundEvent {
        if (eventType == null || eventType.isBlank() || eventType.length() > 128
                || subjectId == null || subjectId.isBlank() || subjectId.length() > 128) {
            throw new IllegalArgumentException("Notification event type and subject are required");
        }
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
