# Notification Center

> **Module:** `notification-module`, `outbox-event-module`, `shared-kernel`
> **Last Updated:** 2026-05-20

## Architecture

The notification center is a multi-channel event-driven system built on Spring application events, with support for in-app inbox, email, SMS, webhook, and third-party (Novu) delivery.

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Notification Architecture                     │
│                                                                      │
│  ┌─────────────┐    ┌──────────────────┐    ┌────────────────────┐  │
│  │  Domain      │    │  Spring           │    │  Notification      │  │
│  │  Events      │───▶│  ApplicationEvent │───▶│  EventHandler      │  │
│  │  (Render,    │    │  Publisher        │    │  (sync delivery)   │  │
│  │   Artifact)  │    └──────────────────┘    └────────┬───────────┘  │
│  └─────────────┘                                      │              │
│                                                       ▼              │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              SpringNotificationEventPublisher                │    │
│  │  (publishToUser: subscription check → channel routing →      │    │
│  │   preference check → inbox creation → provider delivery)     │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                              │                                       │
│              ┌───────────────┼───────────────┐                      │
│              ▼               ▼               ▼                      │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐            │
│  │ IN_APP       │ │ Novu Provider│ │ Local Providers  │            │
│  │ (inbox)      │ │ (remote API) │ │ (email/sms/      │            │
│  │              │ │              │ │  webhook/mock)   │            │
│  └──────────────┘ └──────────────┘ └──────────────────┘            │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Outbox Integration                        │    │
│  │  OutboxBackedNotificationEventPublisher (@Primary)           │    │
│  │  Appends events to outbox table with idempotency keys        │    │
│  └─────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

## Event Definition Catalog (25 Built-in Events)

The system seeds 25 built-in notification events on startup via `NotificationEventCatalogService.seedBuiltInEvents()`. Events are stored in the `notification_event_definition` table.

### Render Events (4)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `render.job.completed` | Render Job Completed | INFO | RENDER | No | Yes |
| `render.job.failed` | Render Job Failed | ERROR | RENDER | No | Yes |
| `render.job.requires_review` | Render Job Requires Review | WARNING | RENDER | No | No |
| `render.job.cancelled` | Render Job Cancelled | INFO | RENDER | No | Yes |

### Quota Events (2)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `quota.usage.warning` | Quota Usage Warning | WARNING | QUOTA | No | Yes |
| `quota.exceeded` | Quota Exceeded | ERROR | QUOTA | Yes | Yes |

### Billing Events (2)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `credits.low` | Low Credits | WARNING | BILLING | No | Yes |
| `billing.invoice.generated` | Invoice Generated | INFO | BILLING | No | Yes |
| `billing.payment.failed` | Payment Failed | ERROR | BILLING | Yes | Yes |

### Entitlement Events (2)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `entitlement.granted` | Entitlement Granted | INFO | ENTITLEMENT | No | Yes |
| `entitlement.revoked` | Entitlement Revoked | WARNING | ENTITLEMENT | Yes | Yes |

### Resource Events (2)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `resource.shared` | Resource Shared | INFO | RESOURCE | No | Yes |
| `resource.invite.received` | Resource Invite Received | INFO | RESOURCE | No | Yes |

### Feedback Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `feedback.updated` | Feedback Updated | INFO | FEEDBACK | No | Yes |

### Report Events (3)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `report.completed` | Report Completed | INFO | REPORT | No | Yes |
| `report.failed` | Report Failed | ERROR | REPORT | No | Yes |
| `nlq.query.failed` | NLQ Query Failed | ERROR | REPORT | No | Yes |

### Prompt Events (3)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `prompt.execution.completed` | Prompt Execution Completed | INFO | SYSTEM | No | Yes |
| `prompt.execution.failed` | Prompt Execution Failed | ERROR | SYSTEM | No | Yes |
| `prompt.risk_review_required` | Prompt Risk Review Required | WARNING | SYSTEM | No | No |

### Extension Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `extension.execution.failed` | Extension Execution Failed | ERROR | SYSTEM | No | Yes |

### Provider Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `provider.health.degraded` | Provider Health Degraded | WARNING | PROVIDER | No | No |

### Worker Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `worker.offline` | Worker Offline | ERROR | WORKER | No | No |

### Security Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `security.suspicious_activity` | Suspicious Activity | CRITICAL | SECURITY | Yes | Yes |

### System Events (1)

| Event Key | Name | Severity | Category | Critical | User Configurable |
|-----------|------|----------|----------|----------|-------------------|
| `system.announcement` | System Announcement | INFO | SYSTEM | No | No |

### Event Definition Schema

```java
public record NotificationEventDefinition(
    String eventKey,              // Unique identifier (e.g., "render.job.completed")
    String name,                  // Human-readable name
    String description,           // What triggers this event
    String category,              // RENDER, BILLING, SECURITY, etc.
    String severity,              // LOW, MEDIUM, HIGH, CRITICAL
    String visibility,            // PUBLIC, INTERNAL, ADMIN_ONLY, SYSTEM_ONLY
    boolean userConfigurable,     // Can users subscribe/unsubscribe
    boolean critical,             // Cannot be disabled by users
    boolean defaultEnabled,       // Default subscription state
    List<String> supportedChannels, // IN_APP, EMAIL, SMS, WEBHOOK
    List<String> requiredPermissions,
    List<String> requiredEntitlements,
    String featureFlagKey,        // Optional FF gating
    String novuWorkflowId,        // Novu workflow identifier
    String localTemplateKey,      // Local template key
    boolean archived,             // Soft delete
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

## Notification Channels

### IN_APP

Delivered to the `notification_user_inbox` table. Users see notifications in:
- `NotificationBell` dropdown (header, last 10 items)
- `NotificationDropdown` component (floating panel)
- `MyNotificationsPage` (full inbox at `/me/notifications`)

### EMAIL

`EmailNotificationProvider` — currently a stub that returns `{"accepted":true,"channel":"EMAIL"}`. Production requires SMTP/API provider integration.

### SMS

`SmsNotificationProvider` — currently a stub that returns `{"accepted":true,"channel":"SMS"}`. Production requires Twilio/similar provider integration.

### WEBHOOK

`WebhookNotificationProvider` — currently a stub that returns `{"accepted":true,"channel":"WEBHOOK"}`. Production requires HTTP client dispatch with HMAC signing.

### NOVU

`NovuNotificationProvider` — full Novu API integration via REST client. See `novu-integration.md` for details.

### MOCK (Local Fallback)

`MockNotificationProvider` — records deliveries to `notification_record` table. Used as fallback when Novu is not configured and no specific provider matches the channel.

## Subscription and Preference Model

### Subscription Model

```java
public record NotificationSubscription(
    String subscriptionId,
    String tenantId,
    String workspaceId,
    String userId,
    String eventKey,
    boolean enabled,
    List<String> channels,        // Override default channels
    String frequency,             // IMMEDIATE (only supported value currently)
    Map<String, String> filters,  // Event-specific filters
    String quietHoursStart,
    String quietHoursEnd,
    String quietHoursTimezone,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

### Preference Model

```java
public record NotificationPreference(
    String preferenceId,
    String tenantId,
    String workspaceId,
    String userId,
    boolean globalEnabled,           // Master switch
    Map<String, Boolean> channelEnabled,  // Per-channel on/off
    Map<String, Boolean> eventEnabled,    // Per-event on/off
    String quietHoursStart,
    String quietHoursEnd,
    String quietHoursTimezone,
    String digestMode,               // NONE, HOURLY, DAILY, WEEKLY
    boolean criticalOverride,        // Allow critical during quiet hours
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

### Delivery Decision Chain

When `SpringNotificationEventPublisher.publishToUser()` is called:

1. Check event definition exists → skip if not found
2. Check `preference.globalEnabled` → skip if disabled (unless event is critical)
3. Check subscription status → skip if unsubscribed (unless event is critical)
4. Determine target channels (from subscription override or event defaults)
5. Filter by `preference.channelEnabled` → remove disabled channels
6. Filter by `preference.eventEnabled` → remove disabled events (unless critical)
7. For each effective channel:
   - Create delivery record in `notification_delivery_record` (status: PENDING)
   - Route via `NotificationProviderRouter.route(command, channel)`
   - Update delivery record with SENT/FAILED status
   - Record audit event (`NOTIFICATION_DELIVERY_CREATED`, `NOTIFICATION_DELIVERY_SENT`, or `NOTIFICATION_DELIVERY_FAILED`)
8. If IN_APP is in effective channels, create inbox item via `NotificationInboxService`

## Delivery and Retry Mechanism

### Delivery Record

```java
public record NotificationDeliveryRecord(
    String deliveryId,
    String eventKey,
    String tenantId,
    String workspaceId,
    String userId,
    String channelType,
    String provider,
    String status,           // PENDING, SENT, FAILED
    int attempts,
    String payloadRedacted,
    String errorCode,
    String providerMessageId,
    OffsetDateTime createdAt,
    OffsetDateTime sentAt,
    OffsetDateTime failedAt,
    OffsetDateTime nextRetryAt
) {}
```

### Routing Logic

`NotificationProviderRouter.route()`:
1. If Novu is enabled (`novuProvider.isEnabled()`) → route to Novu
2. Otherwise, look up provider by channel code
3. If no provider found → fall back to `MockNotificationProvider`

### Retry

Failed deliveries can be retried via:
- Admin UI: "Retry" button on delivery log entries
- API: `POST /admin/notifications/deliveries/{deliveryId}/retry`
- API: `POST /tenants/{tenantId}/notifications/{notificationId}/retry`

The retry endpoint returns `{"status": "RETRY_QUEUED"}` (stub implementation).

## Integration with Other Systems

### Feature Flags

Events can be gated by feature flags via `NotificationEventDefinition.featureFlagKey`. The `NotificationEventDefinitionPage` admin UI allows setting a feature flag key on each event definition.

### Entitlements

Events can require entitlements via `NotificationEventDefinition.requiredEntitlements`. The notification center checks entitlements during delivery.

### Quota

Notification delivery counts against quota. Error code `NOTIFICATION-429-001` ("Notification quota exceeded") is defined for quota enforcement. Quota events (`quota.usage.warning`, `quota.exceeded`) are built-in notification events.

### Billing

Billing events (`credits.low`, `billing.invoice.generated`, `billing.payment.failed`) are built-in notification events. The `billing.payment.failed` event is critical and cannot be disabled.

### Outbox Integration

`OutboxBackedNotificationEventPublisher` (annotated `@Primary`) persists notification events to the outbox table with idempotency keys:

| Event Type | Idempotency Key Pattern |
|------------|------------------------|
| `render.job.created` | `render.job.created:{renderJobId}` |
| `render.job.status.changed` | `render.job.status.changed:{renderJobId}:{oldStatus}:{newStatus}` |
| `render.job.completed` | `render.job.completed:{renderJobId}` |
| `render.job.failed` | `render.job.failed:{renderJobId}` |
| `artifact.created` | `artifact.created:{artifactId}` |

The `OutboxEventDispatcher` processes outbox entries asynchronously (every 3 seconds by default).

### Domain Event Sources

The `NotificationEventHandler` listens to these Spring application events:

| Source Event | Notification Event Types |
|-------------|-------------------------|
| `RenderJobCreatedEvent` | `render.job.created` |
| `RenderJobStatusChangedEvent` | `render.job.completed`, `render.job.failed`, `render.job.ai_processing`, `render.job.rendering`, `render.job.status.changed` |
| `ArtifactCreatedEvent` | `artifact.created` |
| `NotificationInboundEvent` (direct) | Any custom event type |

### Audit Events

| Event Type | Trigger |
|------------|---------|
| `NOTIFICATION_DELIVERY_CREATED` | Delivery record created |
| `NOTIFICATION_DELIVERY_SENT` | Provider returned SENT |
| `NOTIFICATION_DELIVERY_FAILED` | Provider returned FAILED or exception |
| `NOTIFICATION_SUBSCRIPTION_CREATED` | User subscribes to event |
| `NOTIFICATION_SUBSCRIPTION_UPDATED` | User updates subscription |
| `NOTIFICATION_PREFERENCE_UPDATED` | User updates preferences |
| `NOTIFICATION_CHANNEL_BOUND` | User binds a channel |
| `NOTIFICATION_CHANNEL_VERIFIED` | Channel verified |
| `NOTIFICATION_CHANNEL_TESTED` | Test notification sent |
| `NOTIFICATION_CHANNEL_DISABLED` | Channel disabled |
| `NOTIFICATION_CHANNEL_DELETED` | Channel deleted |
