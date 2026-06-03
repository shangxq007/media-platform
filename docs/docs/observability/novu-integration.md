# Novu Integration

> **Module:** `notification-module/src/main/java/com/example/platform/notification/infrastructure/NovuNotificationProvider.java`
> **Last Updated:** 2026-05-20

## Overview

The Novu notification provider integrates with the [Novu](https://novu.co/) notification infrastructure platform. It enables production-grade multi-channel notification delivery (email, SMS, push, chat, in-app) through Novu's workflow engine.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                  Notification Provider Router                 │
│                                                               │
│  DeliveryCommand ──▶ route(command, channel)                  │
│                       │                                       │
│                       ├─ Novu enabled? ──Yes──▶ Novu Provider │
│                       │                         (REST API)    │
│                       │                                       │
│                       └─ No ──▶ Lookup provider by channel    │
│                                 │                             │
│                                 ├─ Found ──▶ Channel Provider │
│                                 │                             │
│                                 └─ Not found ──▶ Mock Provider│
└──────────────────────────────────────────────────────────────┘
```

### NovuNotificationProvider

`NovuNotificationProvider` implements the `NotificationProvider` interface:

```java
public interface NotificationProvider {
    String channel();           // Returns "NOVU"
    String providerCode();      // Returns "novu"
    DeliveryResult send(DeliveryCommand command);
}
```

The provider is a Spring `@Component` that auto-configures itself based on the presence of an API key.

## Configuration

### Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| `app.notification.novu.api-key` | `""` (empty) | Yes (for Novu) | Novu API key for authentication |
| `app.notification.novu.base-url` | `https://api.novu.co/v1` | No | Novu API base URL (useful for self-hosted Novu) |

### Auto-Enable Logic

```java
this.enabled = apiKey != null && !apiKey.isBlank();
```

If the API key is not configured, `isEnabled()` returns `false` and the provider throws `NOTIFICATION-NOVU-503-001` on send attempts.

### Spring Configuration

The provider is auto-detected via component scanning. No explicit `@Configuration` class is required. The `RestClient` is built from Spring's `RestClient.Builder`:

```java
this.restClient = restClientBuilder.baseUrl(baseUrl).build();
```

## Send Flow

When `NotificationProviderRouter.route()` determines Novu is enabled:

1. Extract `novuWorkflowId` from `command.metadata()`
2. Extract `subscriberId` from `command.metadata()` (falls back to `command.subject()`)
3. Build Novu trigger payload:
   ```json
   {
     "workflowId": "<novuWorkflowId>",
     "to": { "subscriberId": "<subscriberId>" },
     "payload": { "subject": "<subject>", "body": "<body>" }
   }
   ```
4. POST to `{baseUrl}/events/trigger` with `Authorization: ApiKey {apiKey}`
5. Return `DeliveryResult("SENT", responseBody)` on success
6. Return `DeliveryResult("FAILED", errorJson)` on `RestClientException`

### Error Handling

| Condition | Error Code | HTTP | Behavior |
|-----------|-----------|------|----------|
| API key not configured | `NOTIFICATION-NOVU-503-001` | 503 | Throws `PlatformException` |
| Missing `novuWorkflowId` | `NOTIFICATION-NOVU-400-001` | 400 | Throws `PlatformException` |
| REST client exception | — | — | Returns `DeliveryResult("FAILED", ...)` |

## Local Provider Fallback

When Novu is NOT configured (`app.notification.novu.api-key` is empty), the system falls back to local providers:

1. `NotificationProviderRouter` checks `novuProvider.isEnabled()` → false
2. Looks up provider by channel code in the `providerByCode` map
3. Available local providers:
   - `stub-email` (EMAIL channel)
   - `stub-sms` (SMS channel)
   - `local-webhook` (WEBHOOK channel)
   - `mock-notification` (MOCK channel — fallback)
4. If no specific provider matches → uses `MockNotificationProvider`

### MockNotificationProvider

The mock provider:
- Logs the delivery attempt
- Records the delivery in `notification_record` table via `NotificationDeliveryRepository`
- Returns `DeliveryResult("SENT", "{\"accepted\":true,\"channel\":\"MOCK\"}")`

## Current Status: Local-Only

**The system currently operates in local-only mode.** The Novu provider is present and fully implemented, but without `app.notification.novu.api-key` configured, all notifications are delivered through local stub/mock providers.

### What Works Today

| Feature | Status |
|---------|--------|
| Event ingestion from domain events | ✅ Working |
| Subscription/preference checking | ✅ Working |
| In-app inbox delivery | ✅ Working |
| Email delivery | 🔧 Stub (returns success, no actual email) |
| SMS delivery | 🔧 Stub (returns success, no actual SMS) |
| Webhook delivery | 🔧 Stub (returns success, no actual HTTP call) |
| Novu delivery | 🔧 Implemented but requires API key |
| Delivery record persistence | ✅ Working |
| Admin delivery log viewing | ✅ Working |

## Production Deployment Configuration

To enable Novu in production:

### 1. Create Novu Account and Workflow

1. Sign up at https://novu.co (or deploy self-hosted Novu)
2. Create a workflow for each notification event type
3. Note the workflow IDs

### 2. Configure Environment Variables

```bash
# Required
export APP_NOTIFICATION_NOVU_API_KEY="your-novu-api-key"

# Optional (for self-hosted Novu)
export APP_NOTIFICATION_NOVU_BASE_URL="https://your-novu-instance.com/v1"
```

### 3. Set Novu Workflow IDs on Event Definitions

Update each `NotificationEventDefinition` with the corresponding `novuWorkflowId`:

```sql
UPDATE notification_event_definition
SET novu_workflow_id = 'your-workflow-id'
WHERE event_key = 'render.job.completed';
```

Or via the admin UI (`/admin/notifications/events`).

### 4. Subscriber Management

Novu requires subscribers to be created before they can receive notifications. The `subscriberId` in the delivery metadata is set to the user ID. Your application must:

1. Create Novu subscribers when users register
2. Update subscriber profiles when user details change
3. Delete subscribers when users are removed

### 5. Verify Provider Status

Check the admin notification page at `/admin/notifications/overview` → Providers tab to confirm Novu shows ACTIVE status.

### 6. Docker Compose (Self-Hosted Novu)

```yaml
services:
  novu-api:
    image: ghcr.io/novuhq/novu/api:latest
    environment:
      - NODE_ENV=production
      - MONGO_URL=mongodb://mongo:27017/novu
      - REDIS_HOST=redis
    ports:
      - "3000:3000"

  mongo:
    image: mongo:6
    volumes:
      - novu-mongo-data:/data/db

  redis:
    image: redis:7-alpine
    volumes:
      - novu-redis-data:/data
```

## Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `NOTIFICATION-NOVU-503-001` | 503 | Novu notification provider is not configured |
| `NOTIFICATION-NOVU-400-001` | 400 | Missing novuWorkflowId in delivery metadata |
