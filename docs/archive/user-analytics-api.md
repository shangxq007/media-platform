# User Analytics API

> **Last updated**: 2026-05-11
> **Module**: `user-analytics-module`
> **Base path**: `/api/v1/analytics`

## Authentication

All endpoints require the `X-Tenant-ID` header for tenant isolation.

```
X-Tenant-ID: <tenant-id>
```

---

## Endpoints

### Ingest Behavior Event

```
POST /api/v1/analytics/events
```

**Request body:**

```json
{
  "userId": "user-123",
  "eventType": "page_view",
  "action": "click",
  "resourceType": "dashboard",
  "resourceId": "dash_001",
  "metadata": {
    "language": "en",
    "source": "web"
  }
}
```

**Response:** `200 OK`

```json
{
  "eventId": "evt_abc123",
  "tenantId": "tenant-1",
  "userId": "user-123",
  "eventType": "page_view",
  "action": "click",
  "resourceType": "dashboard",
  "resourceId": "dash_001",
  "metadata": { "language": "en", "source": "web" },
  "occurredAt": "2026-01-15T10:30:00Z"
}
```

**Notes:**
- Sensitive metadata keys (password, token, secret, apikey, auth, credential, ssn, creditcard) are automatically stripped.
- `userId` and `eventType` are required.

---

### List Events

```
GET /api/v1/analytics/events?limit=100
```

**Response:** `200 OK` — Array of `UserBehaviorEventResponse`

---

### Get User Profile

```
GET /api/v1/analytics/profiles/{userId}
```

Aggregates the user's behavior events into a profile.

**Response:** `200 OK`

```json
{
  "profileId": "prof_abc123",
  "tenantId": "tenant-1",
  "userId": "user-123",
  "preferredLanguages": ["en", "fr"],
  "featureUsageCounts": { "dashboard": 15, "render-job": 3 },
  "actionCounts": { "view": 10, "click": 8 },
  "totalSessions": 5,
  "totalActions": 18,
  "firstSeenAt": "2026-01-01T00:00:00Z",
  "lastActiveAt": "2026-01-15T10:30:00Z",
  "updatedAt": "2026-01-15T10:35:00Z"
}
```

---

### List Profiles

```
GET /api/v1/analytics/profiles?limit=100
```

**Response:** `200 OK` — Array of `UserProfileResponse`

---

### Get User Habits

```
GET /api/v1/analytics/habits/{userId}
```

Computes activity patterns, peak hours, retention, and top features.

**Response:** `200 OK`

```json
{
  "tenantId": "tenant-1",
  "userId": "user-123",
  "dailyActivityBuckets": { "h09": 5, "h14": 12, "h18": 8 },
  "weeklyActivityPattern": { "MONDAY": 10, "WEDNESDAY": 15 },
  "mostUsedFeatures": ["dashboard", "render-job", "storage"],
  "mostUsedActions": ["view", "click", "submit"],
  "averageSessionDepth": 3.6,
  "peakActivityHour": "h14",
  "peakActivityDay": "WEDNESDAY",
  "retentionDays": 14,
  "computedAt": "2026-01-15T10:35:00Z"
}
```

---

### Get Segment

```
GET /api/v1/analytics/segments/{segmentId}
```

**Response:** `200 OK` or `404 Not Found`

---

### List Segments

```
GET /api/v1/analytics/segments
```

**Response:** `200 OK` — Array of `UserSegmentResponse`

---

### Compute Active Users Segment

```
POST /api/v1/analytics/segments/active?activeWithinDays=30
```

Finds users active within the specified number of days.

**Response:** `200 OK`

```json
{
  "segmentId": "seg_abc123",
  "tenantId": "tenant-1",
  "name": "active_last_30d",
  "description": "Users active in the last 30 days",
  "criteria": { "activeWithinDays": "30" },
  "userIds": ["user-1", "user-2"],
  "userCount": 2,
  "computedAt": "2026-01-15T10:35:00Z"
}
```

---

### Compute Power Users Segment

```
POST /api/v1/analytics/segments/power-users?minActions=100
```

Finds users with at least `minActions` total actions.

**Response:** `200 OK` (same format as active segment)

---

## Data Model

### UserBehaviorEvent

| Field | Type | Description |
|-------|------|-------------|
| eventId | String | Unique event identifier (`evt_` prefix) |
| tenantId | String | Tenant scope |
| userId | String | User identifier |
| eventType | String | Category: `page_view`, `action`, `api_call`, etc. |
| action | String | Specific action: `view`, `click`, `submit`, etc. |
| resourceType | String | Feature or resource type |
| resourceId | String | Optional resource identifier |
| metadata | Map<String,String> | Sanitized metadata (no sensitive keys) |
| occurredAt | Instant | Event timestamp |

### UserProfile

| Field | Type | Description |
|-------|------|-------------|
| profileId | String | Unique profile identifier (`prof_` prefix) |
| tenantId | String | Tenant scope |
| userId | String | User identifier |
| preferredLanguages | Set<String> | Detected from event metadata |
| featureUsageCounts | Map<String,Integer> | Usage count per resource type |
| actionCounts | Map<String,Integer> | Count per action type |
| totalSessions | int | Total session count |
| totalActions | int | Total action count |
| firstSeenAt | Instant | First event timestamp |
| lastActiveAt | Instant | Most recent event timestamp |

### UserHabits

| Field | Type | Description |
|-------|------|-------------|
| tenantId | String | Tenant scope |
| userId | String | User identifier |
| dailyActivityBuckets | Map<String,Integer> | Hourly activity (`h00`–`h23`) |
| weeklyActivityPattern | Map<String,Integer> | Activity per day of week |
| mostUsedFeatures | List<String> | Top 5 features by usage |
| mostUsedActions | List<String> | Top 5 actions by count |
| averageSessionDepth | double | Avg events per active day |
| peakActivityHour | String | Hour with most activity |
| peakActivityDay | String | Day with most activity |
| retentionDays | int | Days between first and last activity |

### UserSegment

| Field | Type | Description |
|-------|------|-------------|
| segmentId | String | Unique segment identifier (`seg_` prefix) |
| tenantId | String | Tenant scope |
| name | String | Segment name |
| description | String | Human-readable description |
| criteria | Map<String, String> | Segment criteria used |
| userIds | List<String> | Matched user IDs |
| userCount | int | Number of matched users |

---

## Internal Endpoints

> These endpoints require internal authentication and should not be exposed publicly.

### Rebuild All Profiles

```
POST /api/v1/analytics/internal/rebuild-profiles
X-Tenant-ID: <tenant-id>
```

Triggers manual profile aggregation for all users in the tenant.

**Response:** `200 OK`

```json
{
  "status": "completed",
  "profilesRebuilt": 42
}
```

---

### Rebuild All Segments

```
POST /api/v1/analytics/internal/rebuild-segments
X-Tenant-ID: <tenant-id>
```

Triggers recomputation of all 6 default segments.

**Response:** `200 OK`

```json
{
  "status": "completed",
  "segmentsRebuilt": 6
}
```

---

### Scheduler Status

```
GET /api/v1/analytics/internal/scheduler-status
X-Tenant-ID: <tenant-id>
```

**Response:** `200 OK`

```json
{
  "lastProfileRebuild": "2026-01-15T02:00:00Z",
  "lastSegmentRebuild": "2026-01-15T03:00:00Z"
}
```

---

## Default Segments

| Segment | Criteria | Description |
|---------|----------|-------------|
| `new_users` | `activeWithinDays: 7` | Users who signed up in the last 7 days |
| `active_users` | `activeWithinDays: 30` | Users active in the last 30 days |
| `power_users` | `minActions: 100` | Users with 100+ total actions |
| `at_risk_users` | `inactiveDays: 30, minActions: 10` | Previously active users inactive for 30+ days |
| `dormant_users` | `inactiveDays: 60` | Users with no activity in 60+ days |
| `failed_render_users` | `minFailures: 3` | Users with 3+ failed render jobs |

---

## Scheduler Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.analytics.scheduler.profiles-cron` | `0 0 2 * * ?` | Cron for daily profile rebuild (2 AM) |
| `app.analytics.scheduler.segments-cron` | `0 0 3 * * ?` | Cron for daily segment rebuild (3 AM) |
| `spring.task.scheduling.pool.size` | `4` | Scheduler thread pool size |

---

## Frontend SDK

A TypeScript SDK is available at `docs/examples/analyticsClient.ts`. See `docs/examples/frontend-analytics-guide.md` for usage examples.
