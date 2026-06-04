# Notification Settings

> **Module:** `frontend/src/pages/user/NotificationSettingsPage.vue`
> **Last Updated:** 2026-05-20

## Overview

The notification settings page (`/me/notification-settings`) provides a tabbed interface for users to manage how and when they receive notifications. It has three tabs: Event Subscriptions, Channel Bindings, and Preferences.

## Page Structure

```
NotificationSettingsPage
├── PageHeader: "Notification Settings"
├── Error Banner (conditional)
├── Critical Events Notice (conditional)
├── Tab Navigation
│   ├── Event Subscriptions tab
│   ├── Channel Bindings tab
│   └── Preferences tab
└── LoadingState / ErrorState (during initial load)
```

### Data Loading

On mount, the page loads four resources in parallel via `Promise.allSettled`:

```typescript
const [cat, subs, chans, prefs] = await Promise.allSettled([
  MeEntitlementAPI.getNotificationEventCatalog(),
  MeEntitlementAPI.getNotificationSubscriptions(),
  MeEntitlementAPI.getNotificationChannels(),
  MeEntitlementAPI.getNotificationPreferences(),
])
```

Each resource is handled independently — failure of one does not block the others.

## Event Subscriptions Tab

The default tab shows the event subscription catalog.

### Event List

Each configurable event is displayed with:

- **Name**: Human-readable event name
- **Severity badge**: CRITICAL / HIGH / MEDIUM / LOW with color coding
- **Category badge**: BILLING, SECURITY, SYSTEM, COLLABORATION, RENDER, EXPORT
- **Description**: What triggers this event
- **Supported channels**: IN_APP, EMAIL, SMS, WEBHOOK, CHAT, PUSH icons
- **Critical badge**: Red "CRITICAL" badge for critical events
- **Toggle**: Checkbox to enable/disable the event

### Critical Events

Critical events (e.g., `quota.exceeded`, `billing.payment.failed`, `entitlement.revoked`, `security.suspicious_activity`) have:

- Disabled toggle checkbox (`:disabled="event.critical || saving"`)
- Warning notice at the top: "The following events are critical and cannot be disabled"
- Error message if user attempts to disable: `NOTIFICATION-403-001` — "Critical notifications cannot be disabled"

### Batch Actions

- **Enable all**: Enables all non-critical configurable events
- **Disable non-critical**: Disables all non-critical configurable events

Both call `MeEntitlementAPI.batchUpdateNotificationSubscriptions(updates)`.

### Toggle Logic

```typescript
async function toggleSubscription(eventKey: string, enabled: boolean) {
  if (event?.critical && !enabled) {
    // Show error: "Critical notifications cannot be disabled"
    return
  }
  await MeEntitlementAPI.updateNotificationSubscription(eventKey, enabled)
  await loadAll()  // Reload all data
}
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/notifications/events` | List user-configurable event definitions |
| GET | `/me/notification-subscriptions` | List user's current subscriptions |
| PUT | `/me/notification-subscriptions/{eventKey}` | Update single subscription |
| POST | `/me/notification-subscriptions/batch-update` | Batch update subscriptions |

## Channel Bindings Tab

The channel bindings tab lets users configure where notifications are sent.

### Supported Channel Types

| Type | Icon | Description |
|------|------|-------------|
| EMAIL | 📧 | Email address |
| SMS | 📱 | Phone number |
| WEBHOOK | 🔗 | HTTP webhook URL |
| CHAT | 💬 | Chat integration |
| PUSH | 🔔 | Push notification |

### Binding List

Each bound channel shows:

- Channel type icon and name
- Masked destination (e.g., `te***@example.com`, `***1234`)
- Verification status: "Verified" (green) or "Unverified" (yellow)
- Disabled status: "Disabled" (gray) if not enabled
- Provider name
- Creation timestamp
- Failure count (if > 0)

### Actions per Binding

| Action | Button | Description |
|--------|--------|-------------|
| Verify | "Verify" | Marks binding as verified (only for unverified) |
| Test | "Test" | Sends a test notification |
| Disable | "Disable" | Disables the binding |
| Delete | "Delete" | Permanently removes the binding |

### Bind Channel Form

The form has three fields:

1. **Channel Type**: Dropdown (EMAIL, SMS, WEBHOOK, CHAT, PUSH)
2. **Destination**: Text input (placeholder changes based on type: "https://..." for WEBHOOK, "email or phone" otherwise)
3. **Webhook Secret**: Password input (only shown when type is WEBHOOK)

On submit, calls `MeEntitlementAPI.bindNotificationChannel(channelType, destination, webhookSecret)`.

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/me/notification-channels` | List user's channel bindings |
| POST | `/me/notification-channels` | Create new binding |
| PUT | `/me/notification-channels/{bindingId}` | Update binding destination |
| POST | `/me/notification-channels/{bindingId}/verify` | Verify binding |
| POST | `/me/notification-channels/{bindingId}/test` | Send test notification |
| POST | `/me/notification-channels/{bindingId}/disable` | Disable binding |
| DELETE | `/me/notification-channels/{bindingId}` | Delete binding |

## Preferences Tab

The preferences tab controls global notification behavior.

### Settings

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| Enable Notifications | Toggle | `true` | Master switch for all notifications |
| Channel Availability | Per-channel toggles | IN_APP=true, EMAIL=true, SMS=false, WEBHOOK=false | Per-channel on/off |
| Quiet Hours Start | Time input | — | Start of quiet hours (e.g., "22:00") |
| Quiet Hours End | Time input | — | End of quiet hours (e.g., "08:00") |
| Quiet Hours Timezone | Text input | "UTC" | Timezone for quiet hours |
| Digest Mode | Select | "NONE" | NONE, HOURLY, DAILY, WEEKLY |
| Critical Override | Toggle | `true` | Allow critical notifications during quiet hours |

### Default Preferences

When no preferences exist, the system creates defaults:

```json
{
  "globalEnabled": true,
  "channelEnabled": { "IN_APP": true, "EMAIL": true, "SMS": false, "WEBHOOK": false },
  "digestMode": "NONE",
  "criticalOverride": true
}
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/me/notification-preferences` | Get user preferences |
| PUT | `/me/notification-preferences` | Update preferences |

## Error Codes

| Code | HTTP | Description | Context |
|------|------|-------------|---------|
| `NOTIFICATION-403-001` | 403 | Critical notifications cannot be disabled | Subscription toggle |
| `NOTIFICATION-400-001` | 400 | Failed to update subscription | Subscription update |
| `NOTIFICATION-500-001` | 500 | Failed to update subscription | Channel/Preference save |
| `NOTIFICATION-400-006` | 400 | Invalid webhook URL | Webhook binding |
| `NOTIFICATION-403-001` | 403 | Webhook URL resolved to private/internal IP | Webhook binding |
| `COMMON-401-001` | 401 | Authentication required | Any endpoint |
| `COMMON-403-001` | 403 | Insufficient permission | Any endpoint |
| `COMMON-500-001` | 500 | Internal server error | Any endpoint |

## Permission / Entitlement Integration

Notification-related permissions and entitlements are documented in `access-control-overview.md`. Key points:

- Channel binding requires the user to have the corresponding entitlement (e.g., `notification.email`, `notification.sms`)
- Critical events bypass subscription checks — they are always delivered regardless of user preferences
- The notification inbox (IN_APP) is always available; other channels require provider configuration
