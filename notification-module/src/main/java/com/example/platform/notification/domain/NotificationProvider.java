package com.example.platform.notification.domain;

public interface NotificationProvider {
    String channel();
    String providerCode();
    DeliveryResult send(DeliveryCommand command);
}