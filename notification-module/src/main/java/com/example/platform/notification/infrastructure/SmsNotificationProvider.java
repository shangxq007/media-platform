package com.example.platform.notification.infrastructure;

import com.example.platform.notification.domain.*;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationProvider implements NotificationProvider {
    @Override public String channel() { return "SMS"; }
    @Override public String providerCode() { return "stub-sms"; }
    @Override public DeliveryResult send(DeliveryCommand command) {
        return new DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"SMS\"}");
    }
}