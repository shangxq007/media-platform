# Access Control Overview: Notifications

> **Module:** `notification-module`, `policy-governance-module`, `entitlement-module`
> **Last Updated:** 2026-05-20

## Overview

This document describes the access control model for the notification center, including the permissions, entitlements, and quotas that govern notification-related features.

## Notification Permissions

Notification-related permissions are checked via `PermissionService.resolvePermissions(userId, tenantId)` and used throughout the notification system.

### Permission List

| Permission | Description | Used For |
|------------|-------------|----------|
| `notification.event.view` | View notification event definitions | Admin event definition page |
| `notification.event.manage` | Create/update/archive event definitions | Admin event CRUD |
| `notification.subscription.manage` | Manage own notification subscriptions | User subscription settings |
| `notification.channel.bind` | Bind notification channels | User channel binding |
| `notification.channel.verify` | Verify notification channels | Channel verification |
| `notification.preference.manage` | Manage notification preferences | User preference settings |
| `notification.inbox.view` | View own notification inbox | User notification list |
| `notification.inbox.manage` | Mark notifications as read | Read/unread management |
| `notification.delivery.view` | View delivery logs | Admin delivery log page |
| `notification.delivery.retry` | Retry failed deliveries | Admin retry functionality |
| `notification.provider.view` | View provider status | Admin provider monitoring |
| `notification.admin` | Full notification administration | All admin operations |

### Permission Enforcement

Permissions are enforced at the API layer via `X-User-Id` header and `TenantContext`. The `MeController` and `NotificationController` extract the user ID from the request and scope all data access to the current tenant.

## Notification Entitlements

Entitlements control which notification channels and features are available to each tier.

### Entitlement List

| Entitlement | Description | Default Tiers |
|-------------|-------------|---------------|
| `notification.inApp` | In-app notification inbox | All tiers (FREE+) |
| `notification.email` | Email notifications | All tiers (FREE+) |
| `notification.sms` | SMS notifications | TEAM+ |
| `notification.webhook` | Webhook notifications | TEAM+ |
| `notification.push` | Push notifications | PRO+ |
| `notification.chat` | Chat notifications (Slack, etc.) | ENTERPRISE |
| `notification.digest` | Digest mode (hourly/daily/weekly) | PRO+ |
| `notification.quietHours` | Quiet hours configuration | All tiers (FREE+) |
| `notification.advancedRouting` | Advanced channel routing rules | ENTERPRISE |

### Entitlement Enforcement

Entitlements are checked during delivery in `SpringNotificationEventPublisher.publishToUser()`:

1. The user's tier is resolved via `EntitlementPolicyService.getTier(tenantId)`
2. Channel availability is checked against the tier's entitlements
3. If the user's tier doesn't include a channel, that channel is skipped

### Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `NOTIFICATION-402-001` | 402 | Entitlement required for this notification feature |

## Notification Quotas

Quotas limit the volume of notifications that can be sent per time period.

### Quota List

| Quota | Description | Default Limit | Scope |
|-------|-------------|---------------|-------|
| `notification_email_count` | Email notifications per month | 100 (FREE), 1000 (TEAM), 10000 (PRO), unlimited (ENTERPRISE) | Per tenant |
| `notification_sms_count` | SMS notifications per month | 10 (FREE), 100 (TEAM), 1000 (PRO), unlimited (ENTERPRISE) | Per tenant |
| `notification_webhook_count` | Webhook calls per month | 1000 (FREE), 10000 (TEAM), 100000 (PRO), unlimited (ENTERPRISE) | Per tenant |
| `notification_push_count` | Push notifications per month | 500 (FREE), 5000 (TEAM), 50000 (PRO), unlimited (ENTERPRISE) | Per tenant |
| `notification_inbox_size` | Max inbox items retained | 100 (FREE), 500 (TEAM), 5000 (PRO), unlimited (ENTERPRISE) | Per user |
| `notification_channel_bindings` | Max channel bindings per user | 3 (FREE), 10 (TEAM), 50 (PRO), unlimited (ENTERPRISE) | Per user |

### Quota Enforcement

Quota enforcement is triggered during delivery. When a quota is exceeded:

1. The delivery is skipped for the exceeded channel
2. A `NOTIFICATION-429-001` error is recorded
3. A `quota.exceeded` notification event is published (if the user is subscribed)

### Quota Events

Two built-in notification events are quota-related:

| Event | Severity | Critical | Trigger |
|-------|----------|----------|---------|
| `quota.usage.warning` | WARNING | No | Usage exceeds 80% of limit |
| `quota.exceeded` | ERROR | Yes | Usage exceeds 100% of limit |

## Access Decision Integration

The notification system integrates with the platform's access decision chain:

```
AccessDecisionService.check()
  ├─ RBAC Permission Check
  │  └─ notification.* permissions
  ├─ ABAC Policy Check
  │  └─ Policy rules may reference notification features
  ├─ Feature Flag Check
  │  └─ notification.* feature flags
  ├─ Entitlement Check
  │  └─ notification.* entitlements
  ├─ Quota Check
  │  └─ notification_*_count quotas
  └─ Billing Check
     └─ Credit balance for paid notification channels
```

## Role-Based Access Summary

| Role | In-App | Email | SMS | Webhook | Push | Chat | Admin |
|------|--------|-------|-----|---------|------|------|-------|
| VIEWER | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MEMBER | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| ADMIN | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ |
| OWNER | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## Data Isolation

All notification data is tenant-scoped:

- `NotificationSubscription.tenantId` — filters subscriptions by tenant
- `NotificationPreference.tenantId` — filters preferences by tenant
- `NotificationChannelBinding.tenantId` — filters channel bindings by tenant
- `NotificationInboxItem.tenantId` — filters inbox items by tenant
- `NotificationDeliveryRecord.tenantId` — filters delivery records by tenant

The `TenantContext.get()` method provides the current tenant ID from the request context. All `/api/v1/me/*` endpoints use this to scope queries.
