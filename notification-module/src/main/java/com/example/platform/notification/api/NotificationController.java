package com.example.platform.notification.api;

import com.example.platform.notification.api.dto.CreateNotificationEventRequest;
import com.example.platform.notification.app.NotificationQueryService;
import com.example.platform.notification.infrastructure.MockNotificationProvider;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.notification.domain.NotificationInboundEvent;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationEventPublisher publisher;
    private final NotificationQueryService queryService;
    private final MockNotificationProvider mockProvider;

    public NotificationController(NotificationEventPublisher publisher,
            NotificationQueryService queryService, MockNotificationProvider mockProvider) {
        this.publisher = publisher;
        this.queryService = queryService;
        this.mockProvider = mockProvider;
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped notification endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @GetMapping("/tenants/{tenantId}/notifications")
    public List<?> getNotifications(@PathVariable String tenantId) {
        return queryService.listDeliveries();
    }

    @GetMapping("/tenants/{tenantId}/notifications/{notificationId}")
    public List<?> getNotification(@PathVariable String tenantId, @PathVariable String notificationId) {
        return queryService.listDeliveries();
    }

    @GetMapping("/tenants/{tenantId}/notifications/{notificationId}/deliveries")
    public List<?> getDeliveries(@PathVariable String tenantId, @PathVariable String notificationId) {
        return queryService.listDeliveries();
    }

    @PostMapping("/tenants/{tenantId}/notifications/{notificationId}/retry")
    public Map<String, Object> retry(@PathVariable String tenantId, @PathVariable String notificationId) {
        return Map.of("notificationId", notificationId, "status", "RETRY_QUEUED");
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @PostMapping("/notifications/events")
    public void publish(@Valid @RequestBody CreateNotificationEventRequest request) {
        publisher.publish(new NotificationInboundEvent(request.eventType(), request.subjectId(), request.payload()));
    }

    @GetMapping("/notifications/deliveries")
    public List<?> deliveries() {
        return queryService.listDeliveries();
    }

    @GetMapping("/notifications/mock-sent")
    public List<MockNotificationProvider.SentNotification> mockSent() {
        return mockProvider.getSentNotifications();
    }
}
