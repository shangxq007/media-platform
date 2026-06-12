package com.example.platform.notification.api;

import com.example.platform.notification.api.dto.*;
import com.example.platform.notification.app.*;
import com.example.platform.notification.domain.NotificationEventDefinition;
import com.example.platform.notification.infrastructure.MockNotificationProvider;
import com.example.platform.notification.infrastructure.NovuNotificationProvider;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.notification.domain.NotificationInboundEvent;
import com.example.platform.shared.web.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "Notification subscriptions, delivery, preferences, and inbox")
public class NotificationController {
    private final NotificationEventPublisher publisher;
    private final NotificationQueryService queryService;
    private final MockNotificationProvider mockProvider;
    private final NotificationEventCatalogService catalogService;
    private final NotificationChannelBindingService channelBindingService;
    private final NotificationSubscriptionService subscriptionService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationInboxService inboxService;
    private final NovuNotificationProvider novuProvider;

    public NotificationController(NotificationEventPublisher publisher,
            NotificationQueryService queryService, MockNotificationProvider mockProvider,
            NotificationEventCatalogService catalogService,
            NotificationChannelBindingService channelBindingService,
            NotificationSubscriptionService subscriptionService,
            NotificationPreferenceService preferenceService,
            NotificationInboxService inboxService,
            NovuNotificationProvider novuProvider) {
        this.publisher = publisher;
        this.queryService = queryService;
        this.mockProvider = mockProvider;
        this.catalogService = catalogService;
        this.channelBindingService = channelBindingService;
        this.subscriptionService = subscriptionService;
        this.preferenceService = preferenceService;
        this.inboxService = inboxService;
        this.novuProvider = novuProvider;
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped notification endpoints
    // -------------------------------------------------------------------------

    @GetMapping("/tenants/{tenantId}/notifications")
    @Operation(summary = "List tenant notifications",
               description = "Retrieve all notifications for a specific tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Cross-tenant access denied")
    })
    public List<?> getNotifications(@PathVariable String tenantId) {
        TenantGuard.assertSameTenant(tenantId);
        return queryService.listDeliveries();
    }

    @GetMapping("/tenants/{tenantId}/notifications/{notificationId}")
    @Operation(summary = "Get tenant notification",
               description = "Retrieve a specific notification for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved notification"),
        @ApiResponse(responseCode = "403", description = "Cross-tenant access denied"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public List<?> getNotification(@PathVariable String tenantId, @PathVariable String notificationId) {
        TenantGuard.assertSameTenant(tenantId);
        return queryService.listDeliveries();
    }

    @GetMapping("/tenants/{tenantId}/notifications/{notificationId}/deliveries")
    @Operation(summary = "Get notification deliveries",
               description = "Retrieve delivery attempts for a specific notification")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries"),
        @ApiResponse(responseCode = "403", description = "Cross-tenant access denied"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public List<?> getDeliveries(@PathVariable String tenantId, @PathVariable String notificationId) {
        TenantGuard.assertSameTenant(tenantId);
        return queryService.listDeliveries();
    }

    @PostMapping("/tenants/{tenantId}/notifications/{notificationId}/retry")
    @Operation(summary = "Retry notification delivery",
               description = "Retry delivery of a failed notification")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry queued successfully"),
        @ApiResponse(responseCode = "403", description = "Cross-tenant access denied"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public Map<String, Object> retry(@PathVariable String tenantId, @PathVariable String notificationId) {
        TenantGuard.assertSameTenant(tenantId);
        return Map.of("notificationId", notificationId, "status", "RETRY_QUEUED");
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @PostMapping("/notifications/events")
    @Operation(summary = "Publish notification event",
               description = "Publish a new notification event to the system")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public void publish(@Valid @RequestBody CreateNotificationEventRequest request) {
        publisher.publish(new NotificationInboundEvent(request.eventType(), request.subjectId(), request.payload()));
    }

    @GetMapping("/notifications/deliveries")
    @Operation(summary = "List all deliveries",
               description = "Retrieve all notification deliveries")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    public List<?> deliveries() {
        return queryService.listDeliveries();
    }

    @GetMapping("/notifications/mock-sent")
    @Operation(summary = "Get mock sent notifications",
               description = "Retrieve notifications sent via the mock provider (testing only)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved mock notifications")
    public List<MockNotificationProvider.SentNotification> mockSent() {
        return mockProvider.getSentNotifications();
    }

    // -------------------------------------------------------------------------
    // User-facing: Event catalog
    // -------------------------------------------------------------------------

    @GetMapping("/notifications/events")
    @Operation(summary = "List subscribable events",
               description = "Retrieve all notification events that users can subscribe to")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved event catalog")
    public List<NotificationEventDefinition> listUserSubscribableEvents() {
        return catalogService.listUserConfigurableEvents();
    }

    @GetMapping("/notifications/events/{eventKey}")
    @Operation(summary = "Get event detail",
               description = "Retrieve details of a specific notification event")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved event detail"),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public NotificationEventDefinition getEventDetail(@PathVariable String eventKey) {
        return catalogService.getRequired(eventKey);
    }

    // -------------------------------------------------------------------------
    // User-facing: Channel bindings
    // -------------------------------------------------------------------------

    @GetMapping("/me/notification-channels")
    @Operation(summary = "List user channel bindings",
               description = "Retrieve all notification channel bindings for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved channel bindings"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<?> getUserChannelBindings(HttpServletRequest request) {
        String userId = resolveAuthenticatedUserId(request);
        return channelBindingService.listUserBindings(userId);
    }

    @PostMapping("/me/notification-channels")
    @Operation(summary = "Create channel binding",
               description = "Create a new notification channel binding (e.g., email, SMS, webhook)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel binding created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Map<String, Object> createChannelBinding(HttpServletRequest request,
            @Valid @RequestBody CreateChannelBindingRequest req) {
        String userId = resolveAuthenticatedUserId(request);
        var binding = channelBindingService.createBinding(userId, req.channelType(), req.destination());
        return Map.of("bindingId", binding.bindingId(), "channelType", binding.channelType(),
                "verificationStatus", binding.verificationStatus());
    }

    @PutMapping("/me/notification-channels/{bindingId}")
    @Operation(summary = "Update channel binding",
               description = "Update an existing notification channel binding")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel binding updated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Binding not found")
    })
    public Map<String, Object> updateChannelBinding(HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestBody UpdateChannelBindingRequest req) {
        String userId = resolveAuthenticatedUserId(request);
        var binding = channelBindingService.updateBinding(bindingId, userId, req.destination());
        return Map.of("bindingId", binding.bindingId(), "channelType", binding.channelType(),
                "verificationStatus", binding.verificationStatus());
    }

    @PostMapping("/me/notification-channels/{bindingId}/verify")
    @Operation(summary = "Verify channel binding",
               description = "Trigger verification for a channel binding (e.g., send verification email)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verification initiated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Binding not found")
    })
    public Map<String, Object> verifyChannelBinding(HttpServletRequest request,
            @PathVariable String bindingId) {
        String userId = resolveAuthenticatedUserId(request);
        var binding = channelBindingService.verifyBinding(bindingId, userId);
        return Map.of("bindingId", binding.bindingId(), "verified", binding.verified(),
                "verificationStatus", binding.verificationStatus());
    }

    @PostMapping("/me/notification-channels/{bindingId}/test")
    @Operation(summary = "Test channel binding",
               description = "Send a test notification through a channel binding")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Test notification sent"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Binding not found")
    })
    public Map<String, Object> testChannelBinding(HttpServletRequest request,
            @PathVariable String bindingId) {
        String userId = resolveAuthenticatedUserId(request);
        channelBindingService.testBinding(bindingId, userId);
        return Map.of("bindingId", bindingId, "status", "TEST_SENT");
    }

    @PostMapping("/me/notification-channels/{bindingId}/disable")
    @Operation(summary = "Disable channel binding",
               description = "Disable a notification channel binding")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel binding disabled"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Binding not found")
    })
    public Map<String, Object> disableChannelBinding(HttpServletRequest request,
            @PathVariable String bindingId,
            @RequestParam(required = false, defaultValue = "User disabled") String reason) {
        String userId = resolveAuthenticatedUserId(request);
        var binding = channelBindingService.disableBinding(bindingId, userId, reason);
        return Map.of("bindingId", binding.bindingId(), "enabled", binding.enabled(),
                "disabledReason", binding.disabledReason() != null ? binding.disabledReason() : "");
    }

    @DeleteMapping("/me/notification-channels/{bindingId}")
    @Operation(summary = "Delete channel binding",
               description = "Delete a notification channel binding")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel binding deleted"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Binding not found")
    })
    public Map<String, String> deleteChannelBinding(HttpServletRequest request,
            @PathVariable String bindingId) {
        String userId = resolveAuthenticatedUserId(request);
        channelBindingService.deleteBinding(bindingId, userId);
        return Map.of("status", "DELETED");
    }

    // -------------------------------------------------------------------------
    // User-facing: Subscriptions
    // -------------------------------------------------------------------------

    @GetMapping("/me/notification-subscriptions")
    @Operation(summary = "List user subscriptions",
               description = "Retrieve all notification subscriptions for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved subscriptions"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<?> getUserSubscriptions(HttpServletRequest request) {
        String userId = resolveAuthenticatedUserId(request);
        return subscriptionService.listSubscribableEvents(userId);
    }

    @PutMapping("/me/notification-subscriptions/{eventKey}")
    @Operation(summary = "Update subscription",
               description = "Update a notification subscription for a specific event")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscription updated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public Map<String, Object> updateSubscription(HttpServletRequest request,
            @PathVariable String eventKey,
            @RequestBody UpdateSubscriptionRequest req) {
        String userId = resolveAuthenticatedUserId(request);
        var sub = subscriptionService.upsertSubscription(userId, eventKey, req.enabled(), req.channels());
        return Map.of("eventKey", sub.eventKey(), "enabled", sub.enabled(),
                "channels", sub.channels());
    }

    @PostMapping("/me/notification-subscriptions/batch-update")
    @Operation(summary = "Batch update subscriptions",
               description = "Update multiple notification subscriptions at once")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriptions updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<?> batchUpdateSubscriptions(HttpServletRequest request,
            @Valid @RequestBody BatchUpdateSubscriptionRequest req) {
        String userId = resolveAuthenticatedUserId(request);
        return subscriptionService.batchUpdate(userId, req.updates());
    }

    // -------------------------------------------------------------------------
    // User-facing: Preferences
    // -------------------------------------------------------------------------

    @GetMapping("/me/notification-preferences")
    @Operation(summary = "Get user preferences",
               description = "Retrieve notification preferences for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved preferences"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Map<String, Object> getUserPreferences(HttpServletRequest request) {
        String userId = resolveAuthenticatedUserId(request);
        var pref = preferenceService.getPreferences(userId);
        return Map.of("globalEnabled", pref.globalEnabled(),
                "channelEnabled", pref.channelEnabled(),
                "eventEnabled", pref.eventEnabled(),
                "quietHoursStart", pref.quietHoursStart() != null ? pref.quietHoursStart() : "",
                "quietHoursEnd", pref.quietHoursEnd() != null ? pref.quietHoursEnd() : "",
                "quietHoursTimezone", pref.quietHoursTimezone() != null ? pref.quietHoursTimezone() : "",
                "digestMode", pref.digestMode(),
                "criticalOverride", pref.criticalOverride());
    }

    @PutMapping("/me/notification-preferences")
    @Operation(summary = "Update preferences",
               description = "Update notification preferences for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Map<String, Object> updatePreferences(HttpServletRequest request,
            @RequestBody UpdatePreferenceRequest req) {
        String userId = resolveAuthenticatedUserId(request);
        var pref = preferenceService.updatePreferences(userId,
                req.globalEnabled() != null ? req.globalEnabled() : true,
                req.channelEnabled(),
                req.eventEnabled(),
                req.quietHoursStart(),
                req.quietHoursEnd(),
                req.quietHoursTimezone(),
                req.digestMode(),
                req.criticalOverride() != null ? req.criticalOverride() : true);
        return Map.of("globalEnabled", pref.globalEnabled(),
                "digestMode", pref.digestMode(),
                "criticalOverride", pref.criticalOverride());
    }

    // -------------------------------------------------------------------------
    // User-facing: In-app inbox
    // -------------------------------------------------------------------------

    @GetMapping("/me/notifications/inbox")
    @Operation(summary = "Get user inbox",
               description = "Retrieve in-app notification inbox for the current user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved inbox"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public List<?> getUserInbox(HttpServletRequest request,
            @RequestParam(defaultValue = "50") int limit) {
        String userId = resolveAuthenticatedUserId(request);
        return inboxService.listUserInbox(userId, limit);
    }

    @PostMapping("/me/notifications/inbox/{id}/read")
    @Operation(summary = "Mark inbox item as read",
               description = "Mark a specific inbox notification as read")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inbox item marked as read"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Inbox item not found")
    })
    public Map<String, Object> markInboxRead(HttpServletRequest request,
            @PathVariable String id) {
        String userId = resolveAuthenticatedUserId(request);
        var item = inboxService.markAsRead(id, userId);
        Map<String, Object> notFound = new java.util.HashMap<>();
        notFound.put("error", "NOT_FOUND");
        return item.map(i -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", i.id());
            map.put("read", i.read());
            map.put("readAt", i.readAt() != null ? i.readAt().toString() : "");
            return map;
        }).orElse(notFound);
    }

    // -------------------------------------------------------------------------
    // Admin: Event management
    // -------------------------------------------------------------------------

    @GetMapping("/admin/notifications/events")
    @Operation(summary = "Admin: List all events",
               description = "Retrieve all notification event definitions (admin only)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved event definitions")
    public List<NotificationEventDefinition> adminListEvents() {
        return catalogService.listAllEvents();
    }

    @PostMapping("/admin/notifications/events")
    @Operation(summary = "Admin: Create event definition",
               description = "Create a new notification event definition (admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event definition created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public NotificationEventDefinition adminCreateEvent(@Valid @RequestBody CreateEventDefinitionRequest request) {
        NotificationEventDefinition definition = new NotificationEventDefinition(
                request.eventKey(), request.name(), request.description(),
                request.category(), request.severity(), request.visibility(),
                request.userConfigurable(), request.critical(), request.defaultEnabled(),
                request.supportedChannels() != null ? request.supportedChannels() : List.of("IN_APP", "EMAIL"),
                request.requiredPermissions() != null ? request.requiredPermissions() : List.of(),
                request.requiredEntitlements() != null ? request.requiredEntitlements() : List.of(),
                request.featureFlagKey(), request.novuWorkflowId(), request.localTemplateKey(),
                false, null, null
        );
        return catalogService.create(definition);
    }

    @PutMapping("/admin/notifications/events/{eventKey}")
    @Operation(summary = "Admin: Update event definition",
               description = "Update an existing notification event definition (admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event definition updated successfully"),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public NotificationEventDefinition adminUpdateEvent(@PathVariable String eventKey,
            @RequestBody UpdateEventDefinitionRequest request) {
        NotificationEventDefinition existing = catalogService.getRequired(eventKey);
        NotificationEventDefinition updated = new NotificationEventDefinition(
                eventKey,
                request.name() != null ? request.name() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                request.category() != null ? request.category() : existing.category(),
                request.severity() != null ? request.severity() : existing.severity(),
                request.visibility() != null ? request.visibility() : existing.visibility(),
                request.userConfigurable() != null ? request.userConfigurable() : existing.userConfigurable(),
                request.critical() != null ? request.critical() : existing.critical(),
                request.defaultEnabled() != null ? request.defaultEnabled() : existing.defaultEnabled(),
                request.supportedChannels() != null ? request.supportedChannels() : existing.supportedChannels(),
                request.requiredPermissions() != null ? request.requiredPermissions() : existing.requiredPermissions(),
                request.requiredEntitlements() != null ? request.requiredEntitlements() : existing.requiredEntitlements(),
                request.featureFlagKey() != null ? request.featureFlagKey() : existing.featureFlagKey(),
                request.novuWorkflowId() != null ? request.novuWorkflowId() : existing.novuWorkflowId(),
                request.localTemplateKey() != null ? request.localTemplateKey() : existing.localTemplateKey(),
                existing.archived(), existing.createdAt(), null
        );
        return catalogService.update(eventKey, updated);
    }

    @PostMapping("/admin/notifications/events/{eventKey}/archive")
    @Operation(summary = "Admin: Archive event",
               description = "Archive a notification event definition (admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event archived successfully"),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    public Map<String, String> adminArchiveEvent(@PathVariable String eventKey) {
        catalogService.archive(eventKey);
        return Map.of("eventKey", eventKey, "status", "ARCHIVED");
    }

    // -------------------------------------------------------------------------
    // Admin: Delivery management
    // -------------------------------------------------------------------------

    @GetMapping("/admin/notifications/deliveries")
    @Operation(summary = "Admin: List all deliveries",
               description = "Retrieve all notification deliveries (admin only)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved deliveries")
    public List<?> adminListDeliveries() {
        return queryService.listDeliveries();
    }

    @PostMapping("/admin/notifications/deliveries/{deliveryId}/retry")
    @Operation(summary = "Admin: Retry delivery",
               description = "Retry a failed notification delivery (admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retry queued successfully"),
        @ApiResponse(responseCode = "404", description = "Delivery not found")
    })
    public Map<String, Object> adminRetryDelivery(@PathVariable String deliveryId) {
        return Map.of("deliveryId", deliveryId, "status", "RETRY_QUEUED");
    }

    // -------------------------------------------------------------------------
    // Admin: Subscription management
    // -------------------------------------------------------------------------

    @GetMapping("/admin/notifications/subscriptions")
    @Operation(summary = "Admin: List subscriptions",
               description = "List notification subscriptions, optionally filtered by user (admin only)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved subscriptions")
    public List<?> adminListSubscriptions(@RequestParam(required = false) String userId) {
        if (userId != null && !userId.isBlank()) {
            return subscriptionService.listUserSubscriptions(userId);
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Admin: Provider status
    // -------------------------------------------------------------------------

    @GetMapping("/admin/notifications/provider-status")
    @Operation(summary = "Admin: Get provider status",
               description = "Retrieve status of notification providers (admin only)")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved provider status")
    public Map<String, Object> adminProviderStatus() {
        return Map.of(
                "novu", Map.of("enabled", novuProvider.isEnabled()),
                "local", Map.of("enabled", true)
        );
    }

    /**
     * Resolves the authenticated user ID from the JWT subject set by {@code JwtAuthFilter}.
     * Never trusts client-supplied headers for user identity.
     */
    private String resolveAuthenticatedUserId(HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        if (subject == null || ((String) subject).isBlank()) {
            throw new com.example.platform.shared.web.PlatformException(
                    com.example.platform.shared.web.CommonErrorCode.AUTHENTICATION_REQUIRED);
        }
        return (String) subject;
    }
}
