package com.example.platform.notification.infrastructure;

import com.example.platform.notification.app.NotificationDeliveryRepository;
import com.example.platform.notification.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(MockNotificationProvider.class);

    private final NotificationDeliveryRepository deliveryRepository;

    public MockNotificationProvider(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public String providerCode() {
        return "mock-notification";
    }

    @Override
    public DeliveryResult send(DeliveryCommand command) {
        log.info("MockNotificationProvider: sending notification eventId={}, channel={}", command.eventId(), command.channel());
        SentNotification sent = new SentNotification(
                command.eventId(), command.channel(), command.subject(), command.body(),
                java.time.Instant.now());
        deliveryRepository.recordDelivery(sent);
        return new DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"MOCK\"}");
    }

    public List<SentNotification> getSentNotifications() {
        return deliveryRepository.recentDeliveries(100);
    }

    public void clear() {
        // No-op: delivery records are persisted and cannot be cleared without
        // a dedicated repository method. This method exists for backward
        // compatibility with tests that call clear() in @BeforeEach.
    }

    public record SentNotification(
            String eventId,
            String channel,
            String subject,
            String body,
            java.time.Instant sentAt) {}
}
