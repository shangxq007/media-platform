package com.example.platform.notification.infrastructure;

import com.example.platform.notification.app.NotificationDeliveryRepository;
import com.example.platform.notification.app.NotificationDeliveryRepository.DeliveryRecord;
import com.example.platform.notification.domain.DeliveryCommand;
import com.example.platform.notification.domain.DeliveryResult;
import com.example.platform.notification.domain.NotificationProvider;
import java.time.Instant;
import java.util.List;

/** Test-source-only fake; never packaged in a production artifact or discovered by Spring. */
final class MockNotificationProvider implements NotificationProvider {

    private final NotificationDeliveryRepository deliveryRepository;

    MockNotificationProvider(NotificationDeliveryRepository deliveryRepository) {
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
        deliveryRepository.recordDelivery(new DeliveryRecord(
                command.eventId(), command.channel(), providerCode(), "SENT",
                command.subject(), command.body(), Instant.now()));
        return new DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"MOCK\"}");
    }

    List<DeliveryRecord> getSentNotifications() {
        return deliveryRepository.recentDeliveries(100);
    }
}
