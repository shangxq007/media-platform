package com.example.platform.notification.app;

import com.example.platform.shared.notification.NotificationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringNotificationEventPublisher implements NotificationEventPublisher {
    private final ApplicationEventPublisher publisher;

    public SpringNotificationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
