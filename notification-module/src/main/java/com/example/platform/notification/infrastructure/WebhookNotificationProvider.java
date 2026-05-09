package com.example.platform.notification.infrastructure;

import com.example.platform.notification.domain.*;
import org.springframework.stereotype.Component;

@Component
public class WebhookNotificationProvider implements NotificationProvider {
    @Override public String channel() { return "WEBHOOK"; }
    @Override public String providerCode() { return "local-webhook"; }
    @Override public DeliveryResult send(DeliveryCommand command) {
        return new DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"WEBHOOK\"}");
    }
}