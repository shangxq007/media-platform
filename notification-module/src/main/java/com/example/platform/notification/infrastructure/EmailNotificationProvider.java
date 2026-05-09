package com.example.platform.notification.infrastructure;

import com.example.platform.notification.domain.*;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationProvider {
    @Override public String channel() { return "EMAIL"; }
    @Override public String providerCode() { return "stub-email"; }
    @Override public DeliveryResult send(DeliveryCommand command) {
        return new DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"EMAIL\"}");
    }
}