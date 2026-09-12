package com.example.platform.notification.app;

@org.springframework.modulith.NamedInterface("publisher")
public interface NotificationEventPublisher {
    void publish(Object event);
}
