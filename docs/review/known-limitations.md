# Known Limitations

> **Last Updated:** 2026-06-22

## Quota & Billing

### Quota Persistence Not Implemented (Critical)

The `QuotaService` in `quota-billing-module` stores all state in 4 `ConcurrentHashMap` fields (buckets, usage records, policies, idempotency index). There are **no Repository classes, no JDBC persistence, no Flyway tables** for quota data. All quota state is **lost on application restart**. This is the single largest gap blocking production deployment of the quota system.

**Impact:** Quota limits, usage tracking, and threshold events are ephemeral. Cannot survive restarts, cannot be shared across instances.

**Workaround:** Use entitlement-module quota profiles for static limit definitions. Quota enforcement at runtime is best-effort only.

**Planned:** Database-backed persistence with Flyway migration.

### In-Memory Scheduler

The `ScheduleRegistryService` in `scheduler-module` stores job definitions and run history in memory. No cron engine (Quartz/Temporal) is integrated. Scheduling state is lost on restart.

## Notification Center

### Email Provider (Stub)

`EmailNotificationProvider` returns a mock success response without sending actual emails. Production deployment requires integration with an email service (SMTP, SendGrid, AWS SES, etc.).

### SMS Provider (Stub)

`SmsNotificationProvider` returns a mock success response without sending actual SMS messages. Production deployment requires integration with an SMS gateway (Twilio, AWS SNS, etc.).

### Webhook Provider (Stub)

`WebhookNotificationProvider` returns a mock success response without making actual HTTP calls. The security infrastructure (URL validation, HMAC signing) is in place, but the actual HTTP dispatch is not implemented.

### Novu Not Configured

The Novu provider is fully implemented but disabled by default. Without `APP_NOTIFICATION_NOVU_API_KEY`, all non-inbox notifications are delivered through local stub providers. Production requires:
- Novu account setup
- Workflow creation for each event type
- API key configuration
- Subscriber management integration

### No Retry Scheduler

Failed deliveries can be retried manually via the admin UI or API (`POST /admin/notifications/deliveries/{id}/retry`), but there is no automatic retry scheduler. The retry endpoint returns a stub response (`{"status": "RETRY_QUEUED"}`) without actual retry logic.

### No Digest Mode Implementation

The preference model supports digest modes (NONE, HOURLY, DAILY, WEEKLY) and the UI exposes these options, but the delivery system does not batch notifications into digests. All notifications are sent immediately regardless of digest mode setting.

### No Quiet Hours Enforcement

The preference model stores quiet hours (start, end, timezone) and the UI exposes these settings, but the delivery system does not check quiet hours before sending. The `criticalOverride` preference is also stored but not enforced.

### In-Memory Feature Flags

All feature flags are stored in-memory via `ConcurrentHashMap` in `LocalFeatureFlagProvider`. State is lost on restart. The remote OpenFeature provider (Unleash/LaunchDarkly) is configurable but not set up by default.

### No Integration Tests

Tests are unit-only. There are no integration tests with a real database, real email provider, real SMS provider, or real Novu instance.

## Frontend

### Fallback Navigation Only

When the backend `NavigationRegistryService` is unavailable, the frontend falls back to a static route list. Dynamic navigation decisions (feature flag, entitlement, permission-based) are not available in fallback mode.

### No WebSocket for Real-Time Notifications

Notifications are polled every 60 seconds in the `NotificationBell` component. There is no WebSocket or Server-Sent Events (SSE) connection for real-time notification delivery.

### No Push Notification Support

The frontend does not implement browser push notifications (Push API / Service Workers). The `notification.push` channel type exists in the backend but has no frontend integration.

## Backend

### MeController Notification Endpoints Are Stubs

The `MeController` notification endpoints (`GET /api/v1/me/notifications`, `POST /api/v1/me/notifications/{id}/read`) return empty/stub data. The real notification inbox is served by `NotificationController` (`NotificationInboxService`) which is not wired through `MeController`.

### No Webhook Circuit Breaker

While `NotificationChannelBinding` tracks `failureCount`, there is no automatic circuit-breaking logic that disables channels after consecutive failures. This must be implemented when the real webhook HTTP provider is added.

### No Subscriber Management for Novu

The system does not automatically create/update/delete Novu subscribers when users register, update their profiles, or are removed. This integration must be added for production Novu deployment.

### No Notification Template Management UI

Notification templates are seeded via `DataBootstrap` on startup, but there is no admin UI for managing templates (subject, body, locale, version). Templates must be managed directly in the database.

### Delivery Log Pagination (Admin)

The admin delivery log page (`NotificationDeliveryLogPage`) supports pagination in the UI, but the backend endpoint (`GET /admin/notifications/deliveries`) returns all records without server-side pagination.

## Cross-Cutting

### No GDPR/Data Export

Users cannot export their notification data or request deletion of their notification history.

### No Notification Rate Limiting

There is no rate limiting on notification delivery. A misconfigured integration or a runaway event publisher could generate unlimited notifications.

### No Cross-Region Support

The notification system is designed for single-region deployment. There is no multi-region replication or failover for notification delivery.
